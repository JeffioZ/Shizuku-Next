package moe.shizuku.manager.management;

import android.content.pm.PackageInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import rikka.recyclerview.BaseRecyclerViewAdapter;
import rikka.recyclerview.ClassCreatorPool;
import moe.shizuku.manager.authorization.AuthorizationManager;

public class AppsAdapter extends BaseRecyclerViewAdapter<ClassCreatorPool> {

    public static final class HeaderMarker {}

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    private boolean selectionMode = false;
    private final Set<String> selected = new LinkedHashSet<>();
    private SelectionListener selectionListener;

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public boolean isSelected(String packageName) {
        return selected.contains(packageName);
    }

    public int getSelectedCount() {
        return selected.size();
    }

    public List<PackageInfo> getSelectedPackages() {
        List<PackageInfo> result = new ArrayList<>();
        for (Object item : getItems()) {
            if (item instanceof PackageInfo
                    && selected.contains(((PackageInfo) item).packageName)) {
                result.add((PackageInfo) item);
            }
        }
        return result;
    }

    public void startSelection(String packageName) {
        selectionMode = true;
        selected.add(packageName);
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    public void toggleSelection(String packageName) {
        if (!selected.remove(packageName)) {
            selected.add(packageName);
        }
        if (selected.isEmpty()) {
            selectionMode = false;
        }
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (Object item : getItems()) {
            if (item instanceof PackageInfo) {
                selected.add(((PackageInfo) item).packageName);
            }
        }
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectionMode = false;
        selected.clear();
        notifySelectionChanged();
        notifyDataSetChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selected.size());
        }
    }

    public AppsAdapter() {
        super();

        getCreatorPool().putRule(HeaderMarker.class, ToggleAllViewHolder.CREATOR);
        getCreatorPool().putRule(PackageInfo.class, AppViewHolder.CREATOR);
        getCreatorPool().putRule(Object.class, EmptyViewHolder.CREATOR);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItemAt(position).hashCode();
    }

    @Override
    public ClassCreatorPool onCreateCreatorPool() {
        return new ClassCreatorPool();
    }

    public void updateData(List<PackageInfo> data) {
        getItems().clear();
        if (data.isEmpty()) {
            getItems().add(new Object());
        } else {
            getItems().add(new HeaderMarker());
            getItems().addAll(data);
        }
        notifyDataSetChanged();
    }
}
