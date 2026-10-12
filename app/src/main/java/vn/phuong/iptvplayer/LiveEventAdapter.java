package vn.phuong.iptvplayer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** LauTV-style event cards: one card per logical event and a separate server picker. */
public final class LiveEventAdapter extends BaseAdapter {
    public interface Listener {
        void onServersRequested(LiveEvent event);
        void onFavoriteChanged(LiveEvent event, boolean favorite);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final ChannelAdapter logoBinder;
    private final Listener listener;
    private List<LiveEvent> events = new ArrayList<>();
    private String playingEventId = "";

    public LiveEventAdapter(Context context, ChannelAdapter logoBinder, Listener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.logoBinder = logoBinder;
        this.listener = listener;
    }

    public void submit(List<LiveEvent> events) {
        this.events = new ArrayList<>(events == null ? new ArrayList<>() : events);
        notifyDataSetChanged();
    }

    public void setPlayingEvent(String eventId) {
        String next = eventId == null ? "" : eventId;
        if (next.equals(playingEventId)) return;
        playingEventId = next;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return events.size(); }
    @Override public LiveEvent getItem(int position) { return events.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_live_event, parent, false);
            holder = new Holder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }

        LiveEvent event = getItem(position);
        holder.title.setText(event.title());
        holder.group.setText(event.group().isEmpty() ? "LIVE" : "LIVE  •  " + event.group());
        int servers = event.sources().size();
        holder.servers.setText(servers + " Server" + "  ▸");
        holder.duration.setText("Đã live " + event.elapsedLiveMinutes(System.currentTimeMillis()) + " phút");
        holder.servers.setContentDescription("Chọn máy chủ phát sóng, " + servers + " nguồn");
        holder.servers.setOnClickListener(v -> listener.onServersRequested(event));

        boolean favorite = LiveEventStore.isFavorite(context, event.id());
        holder.favorite.setText(favorite ? "♥" : "♡");
        holder.favorite.setContentDescription(favorite ? "Bỏ sự kiện khỏi yêu thích" : "Thêm sự kiện vào yêu thích");
        holder.favorite.setOnClickListener(v -> {
            boolean next = LiveEventStore.toggleFavorite(context, event.id());
            holder.favorite.setText(next ? "♥" : "♡");
            holder.favorite.setContentDescription(next ? "Bỏ sự kiện khỏi yêu thích" : "Thêm sự kiện vào yêu thích");
            listener.onFavoriteChanged(event, next);
        });

        holder.badge.setText("LIVE");
        if (logoBinder != null) logoBinder.bindLogo(convertView, event.primary());
        convertView.setBackgroundResource(event.id().equals(playingEventId)
                ? R.drawable.channel_card_playing : R.drawable.live_event_card);
        convertView.setMinimumHeight(Math.round((AppPreferences.isTvInterface(context) ? 92 : 86)
                * context.getResources().getDisplayMetrics().density));
        return convertView;
    }

    private static final class Holder {
        final ImageView logo;
        final TextView badge, title, group, servers, duration, favorite;
        Holder(View root) {
            logo = root.findViewById(R.id.imgChannelLogo);
            badge = root.findViewById(R.id.txtChannelBadge);
            title = root.findViewById(R.id.txtLiveEventTitle);
            group = root.findViewById(R.id.txtLiveGroup);
            servers = root.findViewById(R.id.btnLiveServers);
            duration = root.findViewById(R.id.txtLiveDuration);
            favorite = root.findViewById(R.id.btnLiveFavorite);
        }
    }
}
