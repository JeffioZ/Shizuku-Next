# Translations

The app's strings live in [`manager/src/main/res/values/strings.xml`](../manager/src/main/res/values/strings.xml), and
translations come from Crowdin.

- Project: **Shizuku-Next** (id `935085`), source language `en`, mapped by [`crowdin.yml`](../crowdin.yml)
- Two GitHub Actions do the syncing (`.github/workflows/crowdin.yml` and
  `translations-merge.yml`); nothing has to be run locally

## What runs when

| When | What |
| --- | --- |
| A push to `master` that changes `values/strings.xml` | `upload sources` — Crowdin learns what there is to translate |
| Nightly at 03:17, or *Run workflow* | `download translations` into a pull request on the `l10n` branch |
| That pull request | merged automatically, squashed, if it only touches `values-*/strings.xml` and they pass the checks below |

The check and the merge happen **in the same run** as the download, not in a workflow of their
own reacting to the pull request: GitHub starts no runs for events caused by `GITHUB_TOKEN`, and
the pull request is opened with that token, so a separate workflow waits for an event that never
arrives. That is why `merge` sits inside `crowdin.yml`, and why `translations-merge.yml` exists
only for pull requests opened by somebody else.

So the loop is: change a string → push → Crowdin has it → a translator writes it →
the next nightly opens a pull request → it merges itself. The two things that remain a
person's are the push and the translating.

The merge is automatic because a translation cannot break anything the way code can, but the
pull request is checked first, and it is left open when:

- it changes any file other than `manager/src/main/res/values-*/strings.xml`
- a file is not valid XML, or holds no strings at all
- a file is **90% or more the English text**, which means it was exported without
  *skip untranslated strings* and is not a translation

Keys that the source no longer has are reported and do not block: every locale carries a few
from strings renamed upstream, Android ignores a translation with nothing to attach it to,
and the next export drops them.

## The two secrets it needs

Both go in *Settings → Secrets and variables → Actions* on the repository, never in the tree:

- `CROWDIN_PERSONAL_TOKEN` — a Crowdin personal access token with *Source files & strings*
  and *Translations* at Read and Write. Rotate it if it has ever been pasted anywhere else.
- `CROWDIN_PROJECT_ID` — not needed: the id is in the workflow, which is not a secret.

The Crowdin project is also *linked* to this repository through its own GitHub integration,
set up before these workflows existed. That integration uploads sources and pushes its own
translation commits to a `crowdin` branch, and opens a pull request for them that nothing was
merging.

**That side cannot be configured through the Crowdin API** - it has no endpoint for it; the
only place the integration's *push sources* / *push translations* / *create pull requests*
toggles live is the project's Integrations page in the Crowdin web interface. The action uses
`l10n` so the two can never write the same branch, and with both untranslated switches on the
integration's own pull request is now a correct one rather than 193 empty language folders, so
leaving it running costs a duplicate pull request and nothing else. To have one path instead of
two, turn *push translations* off there (or suspend the Crowdin GitHub app once these workflows
have run at least once, since the upload half is the Action's job now).

## Only translated strings are exported

Two switches sound like they do the same thing and do not:

| Setting | Meaning | Here |
| --- | --- | --- |
| *skip untranslated strings* | leave untranslated strings **out of** an exported file, so what is written is only what someone translated | **on** - this is what stopped English text being written into languages that have none |
| *skip untranslated files* | **omit files that are not fully translated** | **off** |

That second one is the trap, and it cost a day: *reading* it as "do not write a file for a
language with nothing in it" is natural and wrong. Since no language here is 100% translated,
turning it on made Crowdin export **nothing at all**, and the sync reported success while
finding no changes, run after run - which is what "the pull request is empty" turned out to mean.

With the first switch off instead, Crowdin writes the source text into every language that has no
translation of its own, which is how this repository came to carry ~200 folders that looked
translated and read as English.

**A locale folder here means someone has translated into that language**, not that the whole file
is translated. Files written before the switch was turned on still carry English text for the
untranslated half; the text is what the app would fall back to anyway, so nobody sees anything
wrong, and each export strips a little more of it. The first sync that ran wrote 23 files, added
483 lines and **removed 8,113**: `values-ja` went from 491 strings to 382, `values-fil` from 491
to 115, and the English copies that were never doing anything are gone.

If your language has no folder, or is missing from the system's app-language list, it has not
been translated yet rather than being broken.

## Which folders the export writes

The project's target languages are **regional locales** - Japanese is `ja` whose Android code is
`ja-rJP`, Russian is `ru-BY`, Indonesian's is the legacy `in-rID` - while this app's folders are
named for the plain language. So Android's own placeholder wrote `values-ja-rJP`, `values-ru-rBY`
and `values-in-rID`: 32 folders the app does not carry, against the one (`values-pt-rBR`) that it
does. Neither placeholder alone gets this right - `%two_letters_code%` collapses `pt-BR`, `zh-CN`
and `zh-TW` instead.

[`crowdin.yml`](../crowdin.yml) therefore names each language explicitly in a `languages_mapping`,
and lists the ten languages the app does not carry in `excluded_target_languages` so their
translations stay in Crowdin rather than arriving as folders the app would then advertise. The
check in `ci/check-translations.py` refuses any file that would create a folder the repository has
not got, so an unmapped language cannot slip in by accident.

**To start shipping a language**: map it in `crowdin.yml`, remove it from the excluded list, and
merge its first pull request by hand - the check only accepts folders that already exist, which is
the point of it.

## Which languages ship

The folders that ship are the ones with real content — currently 24 languages, from Japanese at
129 translated strings down to Brazilian Portuguese at 117. The rest were removed, which also
shrinks the app: `resources.arsc` fell from 3.7 MB to 1.1 MB, taking the release APK from 7.2 MB to
4.6 MB.

There is nothing to maintain by hand for a language to appear: add nothing while it is untranslated
(Crowdin will not export it), and its folder arrives with the next sync once it has translations.
Deleting a folder removes the language from the system's app-language list, because the list is
generated from the folders (`generateLocaleConfig = true` in `manager/build.gradle`) rather than
from a file in the tree.

## Why the language list is short

Crowdin's free plan counts **hosted words as the source words multiplied by the number of target
languages**, so an unused language is not free — it re-hosts every string the app has. This project
had 314 target languages against a source of about 3,430 words, which is roughly **1,077,000 hosted
words**, and every source update multiplied by all of them. That reached the free plan's limit; the
project now holds **33 languages** (about 113,000 hosted words), the ones with translations, which
is what the limit was exceeded by.

So adding a language is a real cost: roughly **3,400 hosted words each**, one language's worth of
the whole app. Add the ones with translators behind them, and remove a language rather than leave
it sitting at 0% (its translations are kept in this repository either way, so nothing is lost by
removing an empty one — and re-adding it takes a moment).

If the account is still over its limit, the dashboard's usage figure is the authority: the number
of languages multiplied by 3,430 gets you there, and 15 languages would be about 51,000 hosted
words.

## Checking progress without the web interface

With a Crowdin personal access token in `CROWDIN_TOKEN` (Account → API tokens; the one for this
project is held by the maintainer), progress can be read straight from the API:

```bash
CROWDIN_TOKEN=… python3 - <<'EOF'
import json, os, urllib.request
base = "https://api.crowdin.com/api/v2"
headers = {"Authorization": "Bearer " + os.environ["CROWDIN_TOKEN"], "Accept": "application/json"}
def get(path):
    with urllib.request.urlopen(urllib.request.Request(base + path, headers=headers), timeout=60) as r:
        return json.load(r)
rows = [i["data"] for i in get("/projects/935085/languages/progress?limit=500")["data"]]
for row in sorted(rows, key=lambda r: -r["translationProgress"])[:20]:
    print(f'{row["languageId"]:10} {row["translationProgress"]:3}%')
EOF
```

At the time of writing every one of the project's 33 languages had a translated string, and the
other 281 were the empty ones that were removed — much of the project's
language list is regional duplicates (`de-BE`, `fr-LU`, `nl-SR`) from when the project was set up,
and Hindi, Arabic, German, French, Russian and Spanish are among the ones with nothing, so their
folders are gone rather than shipping as English.
