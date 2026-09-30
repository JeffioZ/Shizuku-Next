# Translations

The app's strings live in [`manager/src/main/res/values/strings.xml`](../manager/src/main/res/values/strings.xml), and
translations come from Crowdin.

- Project: **Shizuku-Next** (id `935085`), source language `en`
- The project is linked to this repository, so a sync opens a pull request from its `crowdin`
  branch with the translated `values-<language>/strings.xml` files. Merging that request is the
  whole update; nothing has to be run locally.

## Only translated strings are exported

The project is set to **skip untranslated strings**, which matters more than it sounds: with that
off, Crowdin writes the *source* text into a language that has no translation of its own, so the
file looks translated and reads as English. That is what had put ~200 English copies into this
repository (`values-hi/strings.xml` was one: 6 of its 491 strings differed from English, and those
six were out of date), and it is why choosing Hindi in the system's per-language picker changed
nothing — there was nothing behind it.

So **a locale folder in this repository means real translations**. If your language has no folder,
or is missing from the system's app-language list, it has not been translated yet rather than
being broken.

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
