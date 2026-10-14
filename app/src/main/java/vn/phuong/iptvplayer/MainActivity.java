package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@androidx.media3.common.util.UnstableApi
public final class MainActivity extends Activity {
    private static final int OPEN_M3U = 101;
    private static final int PICK_WALLPAPER = 103;
    private static final int MAX_PLAYLIST_BYTES = 8 * 1024 * 1024;
    private static final String DEFAULT_PLAYLIST = PlaylistSourceStore.DEFAULT_URL;

    private final ExecutorService io = SessionStore.IO;
    private final M3uParser parser = new M3uParser();
    private final List<Channel> allChannels = new ArrayList<>();
    // LIVE events come from a dedicated feed and do not mutate the user's IPTV playlist.
    private final List<LiveEvent> liveFeedEvents = new ArrayList<>();
    private LiveEventAdapter liveAdapter;
    private ListView channelList;
    private boolean liveFeedLoading;
    private int liveFeedRequestGeneration;
    private String liveFeedError = "";
    private boolean liveOnly;
    private static final String LIVE_GROUP_TAG = "__NM7_LIVE_EVENTS__";
    private static final long LIVE_FEED_REFRESH_MS = 5L * 60L * 1000L;
    private final android.os.Handler liveFeedHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable liveFeedRefresh = new Runnable() {
        @Override public void run() { loadLiveFeed(); liveFeedHandler.postDelayed(this, LIVE_FEED_REFRESH_MS); }
    };
    private ChannelAdapter adapter;
    private EditText inputUrl, inputSearch;
    private LinearLayout groupRow;
    private TextView txtEmpty;
    private ProgressBar progress;
    private int duplicateCount, missingUrlCount, activeSection;
    private String currentSource = "", selectedGroup = "", epgUrl = "";
    private boolean loading, importExpanded = true;
    private int wallpaperGeneration, playlistRequestGeneration;
    private final android.os.Handler epgHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable epgTick=new Runnable(){@Override public void run(){if(adapter!=null)adapter.notifyDataSetChanged();if(liveAdapter!=null)liveAdapter.notifyDataSetChanged();epgHandler.postDelayed(this,60_000);}};

    @Override protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState); setupViews(); SleepTimer.restore(this); restoreSession(); loadLiveFeed(); liveFeedHandler.postDelayed(liveFeedRefresh, LIVE_FEED_REFRESH_MS); epgHandler.post(epgTick); }
    @Override protected void onDestroy(){epgHandler.removeCallbacksAndMessages(null);liveFeedHandler.removeCallbacksAndMessages(null);super.onDestroy();}

    private void setupViews() {
        setContentView(R.layout.activity_main); Insets.apply(findViewById(R.id.mainRoot)); applyWallpaper();
        inputUrl=findViewById(R.id.inputUrl); inputSearch=findViewById(R.id.inputSearch); groupRow=findViewById(R.id.groupRow);
        txtEmpty=findViewById(R.id.txtEmpty); progress=findViewById(R.id.progress);
        ListView list=findViewById(R.id.listChannels);
        adapter=new ChannelAdapter(this,new ChannelAdapter.Listener(){@Override public void onSelectionChanged(){updateSummary();}@Override public void onFavoriteChanged(Channel c,boolean f){toast(f?"Đã thêm vào Yêu thích":"Đã bỏ khỏi Yêu thích");if(activeSection==1)filter();else adapter.notifyDataSetChanged();}});
        channelList = list;
        liveAdapter = new LiveEventAdapter(this, adapter, new LiveEventAdapter.Listener() {
            @Override public void onServersRequested(LiveEvent event) { showLiveServerPicker(event); }
            @Override public void onFavoriteChanged(LiveEvent event, boolean favorite) {
                toast(favorite ? "Đã thêm sự kiện Live vào Yêu thích" : "Đã bỏ sự kiện Live khỏi Yêu thích");
                if (liveOnly) filter();
            }
        });
        list.setAdapter(adapter);
        list.setItemsCanFocus(false);
        list.setEmptyView(txtEmpty);
        list.setOnItemClickListener((p, v, i, id) -> {
            Object item = p.getAdapter().getItem(i);
            if (item instanceof LiveEvent) playLiveEvent((LiveEvent) item);
            else if (item instanceof Channel) play((Channel) item);
        });
        list.setOnItemLongClickListener((p, v, i, id) -> {
            Object item = p.getAdapter().getItem(i);
            if (item instanceof LiveEvent) { showLiveEventActions((LiveEvent) item); return true; }
            if (item instanceof Channel) { showChannelActions((Channel) item); return true; }
            return false;
        });
        findViewById(R.id.btnLoadUrl).setOnClickListener(v->loadFromUrl()); findViewById(R.id.btnOpenFile).setOnClickListener(v->openFilePicker());
        findViewById(R.id.btnPlayUrl).setOnClickListener(v->playDirect()); findViewById(R.id.btnWallpaper).setOnClickListener(v->showSettings());
        findViewById(R.id.btnClearFilters).setOnClickListener(v->{inputSearch.setText("");selectedGroup="";updateGroupButtons();filter();}); findViewById(R.id.btnReloadPlaylist).setOnClickListener(v->reloadPlaylistUrl());
        inputSearch.addTextChangedListener(new TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){filter();}@Override public void afterTextChanged(Editable e){}});
        rebuildGroups(); setImportExpanded(allChannels.isEmpty()); updateSectionButtons(); applyInterfaceMode(list);
    }

    private void restoreSession() {
        setLoading(true);
        io.execute(()->{try{SessionStore.State state=SessionStore.load(getApplicationContext());ui(()->{
            String savedSource=state==null?"":PlaylistSourceStore.sourceUrl(state.source);
            if(PlaylistSourceStore.isLegacyDefault(savedSource)){loadDefaultPlaylist();return;}
            if(state!=null&&state.result!=null&&!state.result.channels.isEmpty()){
                showPlaylist(state.result,state.source);
                if(PlaylistSourceStore.isValid(savedSource)&&!PlaylistSourceStore.isDefault(savedSource))inputUrl.setText(savedSource);else inputUrl.setText("");
                setLoading(false);
                if(PlaylistSourceStore.shouldRefreshOnStartup(state.source))refreshPlaylistOnStartup(savedSource);
            }else loadDefaultPlaylist();
        });}catch(Exception e){ui(this::loadDefaultPlaylist);}});
    }

    private void loadDefaultPlaylist(){inputUrl.setText("");loadFromUrl(DEFAULT_PLAYLIST,false);}

    private void resetToDefaultPlaylist(){
        currentSource=DEFAULT_PLAYLIST;epgUrl="";duplicateCount=0;missingUrlCount=0;
        allChannels.clear();inputSearch.setText("");selectedGroup="";activeSection=0;
        rebuildGroups();updateSectionButtons();filter();adapter.submitGuide(null);
        inputUrl.setText("");setImportExpanded(false);saveSession();loadFromUrl(DEFAULT_PLAYLIST,false);
    }

    /** Load the independent LIVE M3U feed and refresh it every five minutes. */
    private void loadLiveFeed() {
        if (liveFeedLoading || isFinishing() || isDestroyed()) return;
        liveFeedLoading = true;
        final int request = ++liveFeedRequestGeneration;
        io.execute(() -> {
            String cached = LiveFeedRepository.readCache(getApplicationContext());
            if (!cached.isEmpty()) {
                try {
                    List<LiveEvent> cachedEvents = LiveFeedRepository.parse(cached, System.currentTimeMillis());
                    ui(() -> {
                        if (request != liveFeedRequestGeneration || isFinishing() || isDestroyed()) return;
                        liveFeedEvents.clear(); liveFeedEvents.addAll(cachedEvents);
                        liveFeedError = "";
                        refreshLiveFeedUi();
                    });
                } catch (Exception ignored) { }
            }
            try {
                String content = LiveFeedRepository.download();
                List<LiveEvent> fresh = LiveFeedRepository.parse(content, System.currentTimeMillis());
                LiveFeedRepository.writeCache(getApplicationContext(), content);
                ui(() -> {
                    if (request != liveFeedRequestGeneration || isFinishing() || isDestroyed()) return;
                    liveFeedEvents.clear(); liveFeedEvents.addAll(fresh);
                    liveFeedError = ""; liveFeedLoading = false;
                    refreshLiveFeedUi();
                });
            } catch (Exception failure) {
                String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                ui(() -> {
                    if (request != liveFeedRequestGeneration || isFinishing() || isDestroyed()) return;
                    liveFeedLoading = false;
                    if (liveFeedEvents.isEmpty()) liveFeedError = "Không tải được dữ liệu Live: " + reason;
                    refreshLiveFeedUi();
                });
            }
        });
    }

    private void refreshLiveFeedUi() {
        if (groupRow == null || channelList == null) return;
        rebuildGroups(); updateGroupButtons(); updateSectionButtons(); filter();
    }

    private void reloadPlaylistUrl(){String source=currentSource==null?"":currentSource.split("\\n",2)[0].trim();if(!PlaylistSourceStore.isValid(source)){toast("Playlist hiện tại không phải link URL");return;}if(PlaylistSourceStore.isDefault(source)){inputUrl.setText("");loadFromUrl(source,false);}else{inputUrl.setText(source);loadFromUrl();}}

    private void loadFromUrl(){loadFromUrl(inputUrl.getText().toString().trim(),false);}

    private void refreshPlaylistOnStartup(String source){loadFromUrl(source,true);}

    private void loadFromUrl(String source,boolean backgroundRefresh){
        if(!M3uParser.isNetworkUrl(source)||!(source.startsWith("http://")||source.startsWith("https://"))){
            if(!backgroundRefresh)toast("URL phải bắt đầu bằng http:// hoặc https://");
            setLoading(false);return;
        }
        final int requestGeneration=++playlistRequestGeneration;
        if(!backgroundRefresh)setLoading(true);
        final boolean tv=AppPreferences.isPhysicalTv(this);
        final int maxAttempts=tv?3:1;
        io.execute(()->{
            Exception lastError=null;
            for(int attempt=1;attempt<=maxAttempts;attempt++){
                HttpURLConnection c=null;
                try{
                    URL requestUrl=new URL(source);
                    if("raw.githubusercontent.com".equalsIgnoreCase(requestUrl.getHost())){
                        String separator=source.contains("?")?"&":"?";
                        requestUrl=new URL(source+separator+"_nm7_reload="+System.currentTimeMillis());
                    }
                    c=(HttpURLConnection)requestUrl.openConnection();
                    c.setUseCaches(false);
                    c.setConnectTimeout(tv?25000:15000);
                    c.setReadTimeout(tv?60000:20000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent",tv?"Nm7-IPTV/1.10.25 Android-TV":"Nm7-IPTV/1.10.25 Android");
                    c.setRequestProperty("Accept","application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*");
                    c.setRequestProperty("Connection","keep-alive");
                    c.setRequestProperty("Cache-Control","no-cache, no-store, max-age=0");
                    c.setRequestProperty("Pragma","no-cache");
                    int status=c.getResponseCode();
                    if(status<200||status>=300)throw new Exception("HTTP "+status);
                    String effective=c.getURL().toString(),type=c.getContentType(),description=source.equals(effective)?source:source+"\nChuyển hướng: "+effective;
                    if(type!=null&&(type.startsWith("video/")||type.contains("dash+xml"))){
                        Channel direct=new Channel("Luồng trực tiếp","Phát trực tiếp",effective,"","",java.util.Collections.emptyMap());
                        if(type.contains("dash+xml"))direct.options().add("#KODIPROP:inputstream.adaptive.manifest_type=mpd");
                        ui(()->{if(requestGeneration==playlistRequestGeneration)showPlaylist(new M3uParser.Result(java.util.Collections.singletonList(direct),0,0),description,backgroundRefresh);});
                        return;
                    }
                    String content;
                    try(InputStream s=new BufferedInputStream(c.getInputStream())){content=readText(s);}
                    M3uParser.Result result=parser.parse(content,effective);
                    ui(()->{if(requestGeneration==playlistRequestGeneration)showPlaylist(result,description,backgroundRefresh);});
                    return;
                }catch(Exception e){
                    lastError=e;
                    if(attempt<maxAttempts){
                        int nextAttempt=attempt+1;
                        if(!backgroundRefresh)ui(()->{if(requestGeneration==playlistRequestGeneration)toast("Kết nối chậm • đang thử lại "+nextAttempt+"/"+maxAttempts+"…");});
                        try{Thread.sleep(attempt==1?700L:1600L);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
                    }
                }finally{if(c!=null)c.disconnect();}
            }
            Exception failure=lastError;
            ui(()->{
                if(requestGeneration!=playlistRequestGeneration)return;
                if(backgroundRefresh){setLoading(false);updateSummary();}
                else showError("Không tải được playlist"+(maxAttempts>1?" sau "+maxAttempts+" lần":"")+": "+readable(failure));
            });
        });
    }

    private void openFilePicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,OPEN_M3U);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(request==OPEN_M3U)readLocalFile(uri);if(request==PICK_WALLPAPER){io.execute(()->{try{WallpaperStore.importPhoto(getApplicationContext(),uri);ui(()->{applyWallpaper();toast("Đã đổi hình nền");});}catch(Exception e){ui(()->toast("Không mở được hình nền: "+readable(e)));}});}}
    private void readLocalFile(Uri uri){int requestGeneration=++playlistRequestGeneration;setLoading(true);io.execute(()->{try(InputStream s=getContentResolver().openInputStream(uri)){if(s==null)throw new Exception("Không thể mở tệp");M3uParser.Result r=parser.parse(readText(s),"");ui(()->{if(requestGeneration==playlistRequestGeneration)showPlaylist(r,uri.toString());});}catch(Exception e){ui(()->{if(requestGeneration==playlistRequestGeneration)showError("Không đọc được tệp: "+readable(e));});}});}
    private String readText(InputStream s)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int total=0,n;while((n=s.read(chunk))!=-1){total+=n;if(total>MAX_PLAYLIST_BYTES)throw new Exception("Playlist lớn hơn 8 MB. Với link video, dùng Phát URL.");b.write(chunk,0,n);}return b.toString(StandardCharsets.UTF_8.name());}
    private void showPlaylist(M3uParser.Result r,String source){showPlaylist(r,source,false);}

    private void showPlaylist(M3uParser.Result r,String source,boolean preserveNavigation){
        if(r.channels.isEmpty()){
            setLoading(false);
            if(preserveNavigation){updateSummary();return;}
            new AlertDialog.Builder(this).setTitle("Không có kênh hợp lệ").setMessage("Không thay thế playlist đang mở.").setPositiveButton("Đóng",null).show();return;
        }
        String previous=currentSource.isEmpty()?"":PlaylistSourceStore.sourceUrl(currentSource),next=PlaylistSourceStore.sourceUrl(source);
        if(!r.epgUrl.isEmpty())epgUrl=r.epgUrl;else if(!previous.equals(next))epgUrl="";
        allChannels.clear();allChannels.addAll(r.channels);duplicateCount=r.duplicateCount;missingUrlCount=r.missingUrlCount;currentSource=source;
        if(!preserveNavigation){inputSearch.setText("");selectedGroup="";liveOnly=false;}
        rebuildGroups();filter();setLoading(false);setImportExpanded(false);saveSession();
        if(!epgUrl.isEmpty())loadEpg(false);else adapter.submitGuide(null);
        if(!PlaylistSourceStore.isDefault(next)&&PlaylistSourceStore.isValid(next))try{PlaylistSourceStore.add(this,"",next);}catch(Exception ignored){}
    }

    private void rebuildGroups() {
        Set<String> groups = new LinkedHashSet<>();
        for (Channel channel : allChannels) groups.add(channel.group());
        if (!selectedGroup.isEmpty() && !groups.contains(selectedGroup)) selectedGroup = "";
        groupRow.removeAllViews();
        addGroupButton(getString(R.string.all_groups), "");
        addGroupButton("⚽ LIVE (" + liveFeedEvents.size() + ")", LIVE_GROUP_TAG);
        for (String group : groups) {
            String normalized = group.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("live") || normalized.equals("event") || normalized.equals("events")
                    || normalized.equals("sự kiện") || normalized.equals("su kien") || normalized.equals("sukien")) continue;
            addGroupButton(group, group);
        }
        updateGroupButtons();
    }

    private void addGroupButton(String label, String value) {
        Button button = new Button(this);
        button.setTag(value); button.setText(label); button.setTextSize(13);
        button.setAllCaps(false); button.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(46));
        params.setMarginEnd(dp(8)); groupRow.addView(button, params);
        button.setOnClickListener(view -> {
            String tag = (String) view.getTag();
            if (LIVE_GROUP_TAG.equals(tag)) { liveOnly = true; selectedGroup = ""; activeSection = 0; }
            else { liveOnly = false; selectedGroup = tag; }
            updateGroupButtons(); updateSectionButtons(); filter(); channelList.requestFocus();
        });
    }

    private void updateGroupButtons() {
        if (groupRow == null) return;
        for (int i = 0; i < groupRow.getChildCount(); i++) {
            View child = groupRow.getChildAt(i);
            String tag = (String) child.getTag();
            boolean active = liveOnly ? LIVE_GROUP_TAG.equals(tag) : selectedGroup.equals(tag);
            child.setSelected(active);
            child.setBackgroundResource(active && liveOnly ? R.drawable.live_badge
                    : (active ? R.drawable.button_primary : R.drawable.button_secondary));
            if (child instanceof Button)
                ((Button) child).setTextColor(getColor(active && liveOnly ? android.R.color.white
                        : (active ? R.color.navy : R.color.text_primary)));
        }
    }

    private List<LiveEvent> liveEventsWithSelectedServers() {
        List<LiveEvent> prepared = new ArrayList<>(liveFeedEvents.size());
        for (LiveEvent event : liveFeedEvents)
            prepared.add(event.withPreferredUrl(LiveEventStore.selectedUrl(this, event.id())));
        return prepared;
    }

    private void filter() {
        if (adapter == null || channelList == null) return;
        String query = inputSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (liveOnly) {
            if (channelList.getAdapter() != liveAdapter) channelList.setAdapter(liveAdapter);
            List<LiveEvent> filtered = new ArrayList<>();
            for (LiveEvent event : liveEventsWithSelectedServers()) {
                boolean sectionMatch = activeSection == 0
                        || (activeSection == 1 && LiveEventStore.isFavorite(this, event.id()))
                        || (activeSection == 2 && isRecentLiveEvent(event));
                boolean queryMatch = query.isEmpty() || event.title().toLowerCase(Locale.ROOT).contains(query)
                        || event.group().toLowerCase(Locale.ROOT).contains(query) || liveSourceMatches(event, query);
                if (sectionMatch && queryMatch) filtered.add(event);
            }
            if (activeSection == 2) filtered.sort((a, b) -> Integer.compare(
                    AppPreferences.recentRank(this, a.primary()), AppPreferences.recentRank(this, b.primary())));
            liveAdapter.submit(filtered); updateSectionButtons(); return;
        }
        if (channelList.getAdapter() != adapter) channelList.setAdapter(adapter);
        List<Channel> filtered = new ArrayList<>();
        for (Channel channel : allChannels) {
            boolean groupMatch = selectedGroup.isEmpty() || channel.group().equals(selectedGroup);
            boolean sectionMatch = activeSection == 0
                    || (activeSection == 1 && AppPreferences.isFavorite(this, channel))
                    || (activeSection == 2 && AppPreferences.isRecent(this, channel));
            boolean queryMatch = query.isEmpty() || channel.name().toLowerCase(Locale.ROOT).contains(query)
                    || channel.group().toLowerCase(Locale.ROOT).contains(query)
                    || channel.url().toLowerCase(Locale.ROOT).contains(query);
            if (groupMatch && sectionMatch && queryMatch) filtered.add(channel);
        }
        if (activeSection == 2) filtered.sort((a, b) -> Integer.compare(
                AppPreferences.recentRank(this, a), AppPreferences.recentRank(this, b)));
        adapter.submit(filtered); updateSummary();
    }

    private boolean liveSourceMatches(LiveEvent event, String query) {
        for (Channel source : event.sources()) if (source.url().toLowerCase(Locale.ROOT).contains(query)) return true;
        return false;
    }

    private boolean isRecentLiveEvent(LiveEvent event) {
        for (Channel source : event.sources()) if (AppPreferences.isRecent(this, source)) return true;
        return false;
    }

    private void playLiveEvent(LiveEvent event) {
        if (event == null || event.sources().isEmpty()) return;
        LiveEvent selectedEvent = event.withPreferredUrl(LiveEventStore.selectedUrl(this, event.id()));
        LivePlaybackSession.setActive(selectedEvent);
        if (liveAdapter != null) liveAdapter.setPlayingEvent(selectedEvent.id());

        // Match the 1.10.136 layout: keep MainActivity and its LIVE event list visible,
        // placing the inline player above the filters/list. Only fall back to PlayerActivity
        // if the inline player is not attached (for example, the TV interface is active).
        Channel selected = selectedEvent.primary();
        if (!MobileInlinePlayerProviderV2.playLiveChannel(selected)) {
            play(selected, selectedEvent);
        }
    }

    private void showLiveServerPicker(LiveEvent event) {
        if (event == null || event.sources().isEmpty()) return;
        List<Channel> sources = event.sources();
        if (sources.size() == 1) { playLiveEvent(event); return; }
        String[] labels = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++)
            labels[i] = event.sourceLabel(i) + (i == event.selectedIndex() ? "  ✓ Đang chọn" : "");
        new AlertDialog.Builder(this).setTitle("Chọn máy chủ phát sóng (" + sources.size() + ")")
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= sources.size()) return;
                    Channel source = sources.get(which);
                    LiveEventStore.setSelectedUrl(this, event.id(), source.url());
                    LiveEvent selectedEvent = event.withPreferredUrl(source.url());
                    LivePlaybackSession.setActive(selectedEvent);
                    if (liveAdapter != null) liveAdapter.setPlayingEvent(event.id());
                    dialog.dismiss(); filter(); playLiveEvent(selectedEvent);
                }).setNegativeButton("Đóng", null).show();
    }

    private void showLiveEventActions(LiveEvent event) {
        String favorite = LiveEventStore.isFavorite(this, event.id()) ? "Bỏ Yêu thích" : "Thêm vào Yêu thích";
        String[] actions = {"Phát sự kiện", "Chọn máy chủ (" + event.sources().size() + ")", favorite};
        new AlertDialog.Builder(this).setTitle(event.title()).setItems(actions, (dialog, which) -> {
            if (which == 0) playLiveEvent(event);
            else if (which == 1) showLiveServerPicker(event);
            else {
                boolean now = LiveEventStore.toggleFavorite(this, event.id());
                toast(now ? "Đã thêm sự kiện Live vào Yêu thích" : "Đã bỏ sự kiện Live khỏi Yêu thích");
                filter();
            }
        }).setNegativeButton("Đóng", null).show();
    }

    private void updateSummary(){}
    private void play(Channel channel) { play(channel, null); }

    private void play(Channel channel, LiveEvent liveEvent) {
        if (liveEvent == null) LivePlaybackSession.clearIfNotLiveSource(channel);
        else LivePlaybackSession.setActive(liveEvent.withPreferredUrl(channel.url()));
        AppPreferences.recordRecent(this, channel);
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(PlayerActivity.EXTRA_NAME, channel.name());
        intent.putExtra(PlayerActivity.EXTRA_URL, channel.url());
        Bundle headers = new Bundle();
        for (java.util.Map.Entry<String, String> entry : channel.headers().entrySet())
            headers.putString(entry.getKey(), entry.getValue());
        intent.putExtra(PlayerActivity.EXTRA_HEADERS, headers);
        intent.putExtra(PlayerActivity.EXTRA_MIME, channel.mimeHint());
        intent.putStringArrayListExtra(PlayerActivity.EXTRA_OPTIONS, new ArrayList<>(channel.options()));
        if (liveEvent != null)
            intent.putExtra(PlayerActivity.EXTRA_LIVE_EVENT, LivePlaybackSession.encode(liveEvent.withPreferredUrl(channel.url())));
        startActivity(intent);
    }
    private void selectSection(int section) {
        activeSection = section; updateSectionButtons(); filter();
    }

    private void updateSectionButtons() {
        if (txtEmpty == null) return;
        if (liveOnly) {
            if (liveFeedEvents.isEmpty())
                txtEmpty.setText(liveFeedLoading ? "Đang tải danh sách trận Live…"
                        : (!liveFeedError.isEmpty() ? liveFeedError : "Hiện không có trận đang diễn ra"));
            else if (activeSection == 1) txtEmpty.setText("Chưa có sự kiện Live yêu thích");
            else if (activeSection == 2) txtEmpty.setText("Chưa có sự kiện Live đã xem");
            else txtEmpty.setText("Không tìm thấy sự kiện LIVE");
        } else {
            txtEmpty.setText(activeSection == 1 ? "Chưa có kênh yêu thích"
                    : activeSection == 2 ? "Chưa có kênh đã xem" : "Không tìm thấy kênh");
        }
    }

    private void saveSession(){try{SessionStore.save(getApplicationContext(),SessionStore.snapshot(allChannels),currentSource,epgUrl,duplicateCount,missingUrlCount);}catch(Exception ignored){}}
    private void setLoading(boolean v){loading=v;if(progress!=null)progress.setVisibility(v?View.VISIBLE:View.GONE);}
    private void setImportExpanded(boolean expanded){importExpanded=expanded;View section=findViewById(R.id.importPanel);if(section!=null)section.setVisibility(expanded?View.VISIBLE:View.GONE);}
    private void showPlaylistSources(){
        List<PlaylistSourceStore.Source> sources=PlaylistSourceStore.load(this);
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(12),dp(4),dp(12),dp(8));
        android.widget.ScrollView scroll=new android.widget.ScrollView(this);scroll.addView(rows,new android.widget.ScrollView.LayoutParams(android.widget.ScrollView.LayoutParams.MATCH_PARENT,android.widget.ScrollView.LayoutParams.WRAP_CONTENT));
        final AlertDialog[] holder=new AlertDialog[1];
        LinearLayout defaultRow=new LinearLayout(this);defaultRow.setOrientation(LinearLayout.VERTICAL);defaultRow.setPadding(dp(6),dp(10),dp(6),dp(12));
        TextView defaultName=new TextView(this);defaultName.setText(PlaylistSourceStore.DEFAULT_NAME);defaultName.setTextSize(18);defaultName.setTextColor(getColor(R.color.text_primary));
        TextView defaultInfo=new TextView(this);defaultInfo.setText("Nguồn mặc định tích hợp sẵn • URL được ẩn");defaultInfo.setTextSize(13);defaultInfo.setTextColor(getColor(R.color.text_secondary));
        Button selectDefault=new Button(this);boolean defaultActive=PlaylistSourceStore.isDefault(currentSource==null?"":currentSource.split("\\n",2)[0].trim());selectDefault.setText(defaultActive?"Đang sử dụng nguồn mặc định":"Chọn nguồn mặc định");selectDefault.setAllCaps(false);selectDefault.setEnabled(!defaultActive);
        defaultRow.addView(defaultName);defaultRow.addView(defaultInfo);defaultRow.addView(selectDefault,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(46)));rows.addView(defaultRow,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        selectDefault.setOnClickListener(v->{holder[0].dismiss();loadDefaultPlaylist();});
        if(sources.isEmpty()){
            TextView empty=new TextView(this);empty.setText("Chưa có nguồn tùy chỉnh. Bạn vẫn có thể dùng nguồn mặc định NM7 IPTV.");empty.setTextSize(16);empty.setPadding(dp(8),dp(18),dp(8),dp(18));rows.addView(empty);
        }
        for(int i=0;i<sources.size();i++){
            final int index=i;PlaylistSourceStore.Source source=sources.get(i);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(6),dp(10),dp(6),dp(10));
            TextView nameView=new TextView(this);nameView.setText(source.name);nameView.setTextSize(18);nameView.setTextColor(getColor(R.color.text_primary));
            TextView urlView=new TextView(this);urlView.setText(source.url);urlView.setTextSize(13);urlView.setTextColor(getColor(R.color.text_secondary));urlView.setMaxLines(2);
            LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,dp(6),0,0);
            Button select=new Button(this);select.setText("Chọn");select.setAllCaps(false);
            Button edit=new Button(this);edit.setText("Sửa");edit.setAllCaps(false);
            Button remove=new Button(this);remove.setText("Xóa");remove.setAllCaps(false);
            LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(0,dp(46),1f);actionParams.setMarginEnd(dp(6));
            actions.addView(select,actionParams);actions.addView(edit,actionParams);LinearLayout.LayoutParams lastParams=new LinearLayout.LayoutParams(0,dp(46),1f);actions.addView(remove,lastParams);
            row.addView(nameView);row.addView(urlView);row.addView(actions);rows.addView(row,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
            select.setOnClickListener(v->{holder[0].dismiss();inputUrl.setText(source.url);loadFromUrl();});
            edit.setOnClickListener(v->{holder[0].dismiss();showSourceEditor(source,index);});
            remove.setOnClickListener(v->{holder[0].dismiss();confirmDeleteSource(source,index);});
        }
        holder[0]=new AlertDialog.Builder(this).setTitle("Quản lý nguồn IPTV").setView(scroll)
                .setPositiveButton("Thêm nguồn",(d,w)->showSourceEditor(null,-1))
                .setNegativeButton("Đóng",null).create();
        holder[0].show();
    }
    private void showSourceEditor(PlaylistSourceStore.Source source,int index){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(22),dp(8),dp(22),0);
        EditText nameInput=new EditText(this);nameInput.setHint("Tên nguồn (có thể để trống)");nameInput.setSingleLine(true);
        EditText urlInput=new EditText(this);urlInput.setHint("https://.../playlist.m3u");urlInput.setSingleLine(true);urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        if(source!=null){nameInput.setText(source.name);urlInput.setText(source.url);}
        form.addView(nameInput,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        form.addView(urlInput,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(source==null?"Thêm nguồn IPTV":"Chỉnh sửa nguồn IPTV").setView(form)
                .setPositiveButton("Lưu",null).setNegativeButton("Hủy",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            try{
                String name=nameInput.getText().toString(),url=urlInput.getText().toString();
                if(source==null)PlaylistSourceStore.add(this,name,url);else PlaylistSourceStore.update(this,index,name,url);
                dialog.dismiss();toast(source==null?"Đã thêm nguồn":"Đã cập nhật nguồn");showPlaylistSources();
            }catch(Exception e){urlInput.setError(readable(e));urlInput.requestFocus();}
        }));
        dialog.show();
    }
    private void confirmDeleteSource(PlaylistSourceStore.Source source,int index){
        new AlertDialog.Builder(this).setTitle("Xóa nguồn IPTV?").setMessage(source.name+"\n"+source.url)
                .setPositiveButton("Xóa",(d,w)->{try{PlaylistSourceStore.remove(this,index);List<PlaylistSourceStore.Source> remaining=PlaylistSourceStore.load(this);toast("Đã xóa nguồn");if(PlaylistSourceStore.shouldReturnToDefault(currentSource,remaining)){resetToDefaultPlaylist();toast("Nguồn đang dùng đã bị xóa • đang tải lại NM7 IPTV");}else showPlaylistSources();}catch(Exception e){showError("Không xóa được nguồn: "+readable(e));}})
                .setNegativeButton("Hủy",null).show();
    }
    private void playDirect(){String source=inputUrl.getText().toString().trim();if(source.isEmpty()){toast("Nhập URL để phát");return;}Channel c=new Channel("URL trực tiếp","Trực tiếp",source,"","",java.util.Collections.emptyMap());play(c);}
    private void showImportTools(){setImportExpanded(true);inputUrl.setText("");inputUrl.requestFocus();}
    private void showEpgEditor(){final EditText field=new EditText(this);field.setHint("https://.../epg.xml hoặc epg.xml.gz");field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);field.setText(epgUrl);field.setSelection(field.length());new AlertDialog.Builder(this).setTitle("Lịch phát sóng XMLTV").setMessage("Ứng dụng tự đọc url-tvg trong playlist. Bạn cũng có thể nhập URL EPG thủ công.").setView(field).setPositiveButton("Tải lịch",(d,w)->{String value=field.getText().toString().trim();if(!value.startsWith("http://")&&!value.startsWith("https://")){toast("URL EPG phải bắt đầu bằng http:// hoặc https://");return;}epgUrl=value;saveSession();loadEpg(true);}).setNeutralButton("Xóa EPG",(d,w)->{epgUrl="";adapter.submitGuide(null);saveSession();toast("Đã xóa lịch phát sóng");}).setNegativeButton("Hủy",null).show();}
    private void loadEpg(boolean notify){String address=epgUrl;io.execute(()->{try{EpgStore.Guide guide=EpgStore.download(address);ui(()->{if(!address.equals(epgUrl))return;adapter.submitGuide(guide);if(notify)toast(guide.size()>0?"Đã cập nhật lịch phát sóng":"Chưa tìm thấy chương trình đang phát");});}catch(Exception error){ui(()->{if(address.equals(epgUrl)&&notify)showError("Không tải được EPG: "+readable(error));});}});}
    private void showSettings(){
        boolean tv=AppPreferences.isTvInterface(this);
        String mode=AppPreferences.interfaceMode(this);
        String modeLabel="tv".equals(mode)?"TV":"mobile".equals(mode)?"Mobile":"Tự động";
        String urls=AppPreferences.showUrls(this)?"Ẩn URL trong danh sách":"Hiện URL trong danh sách";
        String rows=AppPreferences.compactRows(this)?"Hàng kênh thoải mái":"Hàng kênh thu gọn";
        String fps=AppPreferences.showFps(this)?"Ẩn FPS khi xem":"Hiện FPS khi xem";
        String clock=AppPreferences.showClock(this)?"Ẩn đồng hồ khi xem":"Hiện đồng hồ khi xem";
        String playerSource=AppPreferences.showPlayerSource(this)?"Ẩn nguồn phát khi xem":"Hiện nguồn phát khi xem";
        String background=AppPreferences.backgroundPlayback(this)?"Tắt phát nền khi khóa màn hình/nhấn Home":"Bật phát nền khi khóa màn hình/nhấn Home";
        List<String> items=new ArrayList<>();
        final int allIndex=items.size();items.add("Tất cả kênh");
        final int favoriteIndex=items.size();items.add("★ Yêu thích");
        final int recentSectionIndex=items.size();items.add("◷ Gần đây");
        final int manageIndex=items.size();items.add("Quản lý nguồn IPTV");
        final int importIndex=items.size();items.add("Thêm hoặc mở URL/tệp");
        final int reloadIndex=items.size();items.add("Tải lại playlist hiện tại");
        final int epgIndex=items.size();items.add("Lịch phát sóng (EPG)");
        final int interfaceIndex=items.size();items.add("Giao diện: "+modeLabel);
        final int wallpaperIndex=items.size();items.add("Đổi hình nền");
        final int urlsIndex=items.size();items.add(urls);
        final int rowsIndex=items.size();items.add(rows);
        final int fpsIndex=items.size();items.add(fps);
        final int clockIndex=items.size();items.add(clock);
        final int playerSourceIndex=items.size();items.add(playerSource);
        final int sleepTimerIndex=items.size();items.add("Hẹn giờ đóng app");
        final int backgroundIndex;if(tv)backgroundIndex=-1;else{backgroundIndex=items.size();items.add(background);}
        final int clearRecentIndex=items.size();items.add("Xóa lịch sử Gần đây");
        final int aboutIndex=items.size();items.add("Thông tin ứng dụng");
        new AlertDialog.Builder(this).setTitle("Tùy chọn ứng dụng")
                .setItems(items.toArray(new String[0]),(dialog,which)->{
                    if(which==allIndex)selectSection(0);
                    if(which==favoriteIndex)selectSection(1);
                    if(which==recentSectionIndex)selectSection(2);
                    if(which==manageIndex)showPlaylistSources();
                    if(which==importIndex)showImportTools();
                    if(which==reloadIndex)reloadPlaylistUrl();
                    if(which==epgIndex)showEpgEditor();
                    if(which==interfaceIndex)chooseInterfaceMode();
                    if(which==wallpaperIndex)chooseWallpaper();
                    if(which==urlsIndex){AppPreferences.setShowUrls(this,!AppPreferences.showUrls(this));adapter.notifyDataSetChanged();}
                    if(which==rowsIndex){AppPreferences.setCompactRows(this,!AppPreferences.compactRows(this));adapter.notifyDataSetChanged();}
                    if(which==fpsIndex)AppPreferences.setShowFps(this,!AppPreferences.showFps(this));
                    if(which==clockIndex)AppPreferences.setShowClock(this,!AppPreferences.showClock(this));
                    if(which==playerSourceIndex)AppPreferences.setShowPlayerSource(this,!AppPreferences.showPlayerSource(this));
                    if(which==playerSourceIndex+1)SleepTimer.showDialog(this);
                    if(which==sleepTimerIndex)SleepTimer.showDialog(this);
                    if(which==backgroundIndex){boolean enabled=!AppPreferences.backgroundPlayback(this);AppPreferences.setBackgroundPlayback(this,enabled);if(!enabled)stopService(new Intent(this,BackgroundPlaybackService.class));if(enabled&&android.os.Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},104);toast(enabled?"Đã bật phát nền":"Đã tắt phát nền");}
                    if(which==clearRecentIndex){AppPreferences.clearRecent(this);if(activeSection==2)filter();toast("Đã xóa lịch sử");}
                    if(which==aboutIndex)showAbout();
                }).setNegativeButton("Đóng",null).show();
    }
    private void chooseInterfaceMode(){
        String[] labels={"Tự động theo thiết bị","Mobile — cảm ứng","TV — điều khiển D-pad"};
        String[] values={"auto","mobile","tv"};
        new AlertDialog.Builder(this).setTitle("Chọn giao diện")
                .setSingleChoiceItems(labels,java.util.Arrays.asList(values).indexOf(AppPreferences.interfaceMode(this)),(dialog,which)->{
                    AppPreferences.setInterfaceMode(this,values[which]);dialog.dismiss();recreate();
                }).setNegativeButton("Đóng",null).show();
    }
    private void applyInterfaceMode(ListView list){boolean tv=AppPreferences.isTvInterface(this);list.setDividerHeight(dp(tv?3:5));}
    private void chooseWallpaper(){
        new AlertDialog.Builder(this).setTitle("Hình nền")
                .setItems(new String[]{"Xanh đêm","Biển sâu","Tím","Chọn ảnh trên máy"},(dialog,which)->{
                    if(which==3){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/*");startActivityForResult(intent,PICK_WALLPAPER);}
                    else{WallpaperStore.setStyle(this,which);applyWallpaper();}
                }).setNegativeButton("Đóng",null).show();
    }
    private void applyWallpaper(){
        int generation=++wallpaperGeneration;
        io.execute(()->{android.graphics.drawable.Drawable background=WallpaperStore.load(getApplicationContext());ui(()->{if(generation==wallpaperGeneration)findViewById(R.id.mainRoot).setBackground(background);});});
    }
    private void showAbout(){String version;try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;if(version.endsWith("-mobile"))version=version.substring(0,version.length()-7)+" (Mobile)";}catch(Exception ignored){version="Không xác định";}new AlertDialog.Builder(this).setTitle("Nm7 IPTV Player").setMessage("Phiên bản: "+version+"\n\nỨng dụng được phát triển bởi Phuongnm7 vì mục đích cá nhân, không vì mục đích thương mại.").setPositiveButton("Đóng",null).show();}
    private void showChannelActions(Channel c){new AlertDialog.Builder(this).setTitle(c.name()).setItems(new String[]{AppPreferences.isFavorite(this,c)?"Bỏ Yêu thích":"Thêm vào Yêu thích","Phát"},(d,w)->{if(w==0){AppPreferences.toggleFavorite(this,c);filter();}else play(c);}).show();}
    private void ui(Runnable r){runOnUiThread(r);}
    private void showError(String m){setLoading(false);new AlertDialog.Builder(this).setTitle("Lỗi").setMessage(m).setPositiveButton("Đóng",null).show();}
    private String readable(Exception e){return e==null?"Không rõ nguyên nhân":e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private void toast(String m){Toast.makeText(this,m,Toast.LENGTH_SHORT).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}