package com.poupa.vinylmusicplayer.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.kabouzeid.appthemehelper.ThemeStore;
import com.kabouzeid.appthemehelper.util.ATHUtil;
import com.poupa.vinylmusicplayer.R;
import com.poupa.vinylmusicplayer.adapter.base.AbsMultiSelectAdapter;
import com.poupa.vinylmusicplayer.adapter.base.MediaEntryViewHolder;
import com.poupa.vinylmusicplayer.databinding.ItemListSingleRowBinding;
import com.poupa.vinylmusicplayer.dialogs.ClearSmartPlaylistDialog;
import com.poupa.vinylmusicplayer.dialogs.DeletePlaylistDialog;
import com.poupa.vinylmusicplayer.helper.menu.MenuHelper;
import com.poupa.vinylmusicplayer.helper.menu.PlaylistMenuHelper;
import com.poupa.vinylmusicplayer.helper.menu.SongsMenuHelper;
import com.poupa.vinylmusicplayer.interfaces.CabHolder;
import com.poupa.vinylmusicplayer.misc.WeakContextAsyncTask;
import com.poupa.vinylmusicplayer.model.Playlist;
import com.poupa.vinylmusicplayer.model.Song;
import com.poupa.vinylmusicplayer.model.smartplaylist.AbsSmartPlaylist;
import com.poupa.vinylmusicplayer.preferences.SmartPlaylistPreferenceDialog;
import com.poupa.vinylmusicplayer.util.ImageTheme.ThemeStyleUtil;
import com.poupa.vinylmusicplayer.util.MusicUtil;
import com.poupa.vinylmusicplayer.util.NavigationUtil;
import com.poupa.vinylmusicplayer.util.OopsHandler;
import com.poupa.vinylmusicplayer.util.PlaylistsUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
public class PlaylistAdapter extends AbsMultiSelectAdapter<PlaylistAdapter.ViewHolder, Playlist> {

    private static final int SMART_PLAYLIST = 0;
    private static final int DEFAULT_PLAYLIST = 1;

    protected final AppCompatActivity activity;
    protected ArrayList<Playlist> dataSet;

    public PlaylistAdapter(AppCompatActivity activity, ArrayList<Playlist> dataSet, @Nullable CabHolder cabHolder) {
        super(activity, cabHolder, R.menu.menu_playlists_selection);
        this.activity = activity;
        this.dataSet = dataSet;
        setHasStableIds(true);
    }

    public ArrayList<Playlist> getDataSet() {
        return dataSet;
    }

    public void swapDataSet(ArrayList<Playlist> dataSet) {
        this.dataSet = dataSet;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return dataSet.get(position).id;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemListSingleRowBinding binding = ItemListSingleRowBinding.inflate(LayoutInflater.from(activity), parent, false);
        return new ViewHolder(binding, viewType);
    }

    protected String getPlaylistTitle(Playlist playlist) {
        return playlist.name;
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final Playlist playlist = dataSet.get(position);

        holder.itemView.setActivated(isChecked(playlist));

        if (holder.title != null) {
            holder.title.setText(getPlaylistTitle(playlist));
        }
        if (holder.text != null) {
            // This operation lasts seconds
            // long enough to block the UI thread, but short enough to accept the context leak in an async operation
            new AsyncTask<Playlist, Void, String>() {
                @Override
                protected String doInBackground(Playlist... params) {
                    return params[0].getInfoString(activity);
                }

                @Override
                protected void onPostExecute(String info) {
                    holder.text.setText(info);
                }
            }.execute(playlist);
        }

        if (holder.getAdapterPosition() == getItemCount() - 1) {
            if (holder.shortSeparator != null) {
                holder.shortSeparator.setVisibility(View.GONE);
            }
        } else {
            if (holder.shortSeparator != null && !(dataSet.get(position) instanceof AbsSmartPlaylist)) {
                holder.shortSeparator.setVisibility(ThemeStyleUtil.getInstance().getShortSeparatorVisibilityState());
            }
        }

        if (holder.image != null) {
            holder.image.setImageResource(getIconRes(playlist));
        }
    }

    private int getIconRes(Playlist playlist) {
        if (playlist instanceof AbsSmartPlaylist) {
            return ((AbsSmartPlaylist) playlist).iconRes;
        }
        return MusicUtil.isFavoritePlaylist(activity, playlist) ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_queue_music_white_24dp;
    }

    @Override
    public int getItemViewType(int position) {
        return dataSet.get(position) instanceof AbsSmartPlaylist ? SMART_PLAYLIST : DEFAULT_PLAYLIST;
    }

    @Override
    public int getItemCount() {
        return dataSet.size();
    }

    @Override
    protected Playlist getIdentifier(int position) {
        return dataSet.get(position);
    }

    @Override
    protected void onMultipleItemAction(@NonNull MenuItem menuItem, @NonNull ArrayList<Playlist> selection) {
        if (R.id.action_delete_playlist == menuItem.getItemId()) {
            for (int i = 0; i < selection.size(); i++) {
                Playlist playlist = selection.get(i);
                if (playlist instanceof AbsSmartPlaylist) {
                    AbsSmartPlaylist absSmartPlaylist = (AbsSmartPlaylist) playlist;
                    if (absSmartPlaylist.isClearable()) {
                        ClearSmartPlaylistDialog.create(absSmartPlaylist).show(activity.getSupportFragmentManager(), "CLEAR_PLAYLIST_" + absSmartPlaylist.name);
                    }
                    selection.remove(playlist);
                    i--;
                }
            }
            if (selection.size() > 0) {
                DeletePlaylistDialog.create(selection).show(activity.getSupportFragmentManager(), "DELETE_PLAYLIST");
            }
        } else if (R.id.action_save_playlist == menuItem.getItemId()) {
            if (selection.size() == 1) {
                PlaylistMenuHelper.handleMenuClick(activity, selection.get(0), menuItem);
            } else {
                new SavePlaylistsAsyncTask(activity).execute(selection);
            }
        } else {
            SongsMenuHelper.handleMenuClick(activity, getSongList(selection), menuItem.getItemId());
        }
    }

    private static class SavePlaylistsAsyncTask extends WeakContextAsyncTask<ArrayList<Playlist>, String, String> {
        public SavePlaylistsAsyncTask(Context context) {
            super(context);
        }

        @SafeVarargs
        @Override
        protected final String doInBackground(ArrayList<Playlist>... params) {
            int successes = 0;
            int failures = 0;

            String dir = "";
            final Context context = getContext();

            for (Playlist playlist : params[0]) {
                try {
                    dir = PlaylistsUtil.savePlaylist(context, playlist);
                    successes++;
                } catch (IOException e) {
                    OopsHandler.copyStackTraceToClipboard(context, e);
                    failures++;
                }
            }

            return failures == 0
                    ? String.format(context.getString(R.string.saved_x_playlists_to_x), successes, dir)
                    : String.format(context.getString(R.string.saved_x_playlists_to_x_failed_to_save_x), successes, dir, failures);
        }

        @Override
        protected void onPostExecute(String string) {
            super.onPostExecute(string);
            Context context = getContext();
            if (context != null) {
                Toast.makeText(context, string, Toast.LENGTH_LONG).show();
            }
        }
    }

    @NonNull
    private ArrayList<Song> getSongList(@NonNull List<Playlist> playlists) {
        final ArrayList<Song> songs = new ArrayList<>();
        for (Playlist playlist : playlists) {
            songs.addAll(playlist.getSongs(activity));
        }
        return songs;
    }

    public class ViewHolder extends MediaEntryViewHolder {
        public ViewHolder(@NonNull ItemListSingleRowBinding binding, int itemViewType) {
            super(binding);

            if (itemViewType == SMART_PLAYLIST) {
                if (shortSeparator != null) {
                    shortSeparator.setVisibility(View.GONE);
                }
                View itemView = binding.getRoot();
                ThemeStyleUtil.getInstance().setPlaylistCardItemStyle(itemView, activity);
            }

            if (image != null) {
                int iconPadding = activity.getResources().getDimensionPixelSize(R.dimen.list_item_image_icon_padding);
                image.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                image.setColorFilter(ATHUtil.resolveColor(activity, R.attr.iconColor), PorterDuff.Mode.SRC_IN);
            }

            if (menu != null) {
                menu.setOnClickListener(view -> {
                    final Playlist playlist = dataSet.get(getAdapterPosition());
                    final PopupMenu popupMenu = new PopupMenu(activity, view);

                    if (playlist instanceof AbsSmartPlaylist) {
                        popupMenu.inflate(R.menu.menu_item_smart_playlist);
                        final AbsSmartPlaylist smartPlaylist = (AbsSmartPlaylist) playlist;
                        if (!smartPlaylist.isClearable()) {
                            popupMenu.getMenu().findItem(R.id.action_clear_playlist).setVisible(false);
                        }
                        final String prefKey = smartPlaylist.getPlaylistPreference();
                        if (prefKey == null) {
                            popupMenu.getMenu().findItem(R.id.action_playlist_settings).setVisible(false);
                        }
                        popupMenu.setOnMenuItemClickListener(item -> {
                            if (item.getItemId() == R.id.action_clear_playlist) {
                                ClearSmartPlaylistDialog.create(smartPlaylist).show(activity.getSupportFragmentManager(), "CLEAR_SMART_PLAYLIST_" + smartPlaylist.name);
                                return true;
                            }
                            else if (item.getItemId() == R.id.action_playlist_settings) {
                                if (prefKey != null) {
                                    SmartPlaylistPreferenceDialog
                                            .newInstance(prefKey)
                                            .show(activity.getSupportFragmentManager(), "SETTINGS_SMART_PLAYLIST_" + smartPlaylist.name);
                                }
                                return true;
                            }
                            return PlaylistMenuHelper.handleMenuClick(
                                activity, dataSet.get(getAdapterPosition()), item);
                        });
                    }
                    else {
                        popupMenu.inflate(R.menu.menu_item_playlist);

                        MenuHelper.decorateDestructiveItems(popupMenu.getMenu(), activity);

                        popupMenu.setOnMenuItemClickListener(item -> PlaylistMenuHelper.handleMenuClick(
                            activity, dataSet.get(getAdapterPosition()), item));
                    }
                    popupMenu.show();
                });
            }
        }

        @Override
        public void onClick(View view) {
            if (isInQuickSelectMode()) {
                toggleChecked(getAdapterPosition());
            } else {
                Playlist playlist = dataSet.get(getAdapterPosition());
                NavigationUtil.goToPlaylist(activity, playlist);
            }
        }

        @Override
        public boolean onLongClick(View view) {
            setColor(ThemeStore.primaryColor(activity));
            toggleChecked(getAdapterPosition());
            return true;
        }
    }
}
