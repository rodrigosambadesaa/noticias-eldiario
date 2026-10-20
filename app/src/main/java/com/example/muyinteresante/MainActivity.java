package com.example.muyinteresante;

import android.content.Intent;
import androidx.lifecycle.ViewModelProvider;
import android.os.Bundle;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;

import com.example.muyinteresante.util.ConnectivityAndInternetAccess;
import com.example.muyinteresante.util.ConnectivityRequestPolicy;
import com.example.muyinteresante.util.NewsCacheManager;
import com.example.muyinteresanteNoTocar.DescargaNoticiasRSS;
import com.example.muyinteresanteNoTocar.NoticiaRSS;
import com.example.muyinteresanteNoTocar.iNoticiaRSS;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements iNoticiaRSS {

    private static final String TAG = "MainActivity";
    private static final String RSS_URL = "https://www.eldiario.es/rss/";
    private static final String RSS_PAGE_URL = "https://www.eldiario.es/rss/";
    private static final int LOAD_MORE_THRESHOLD = 4;
    private static final int MAX_CONSECUTIVE_DUPLICATE_PAGES = 2;
    private static final int NEWS_PAGE_SIZE = 20;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvNoticias;
    private NoticiasAdapter adapter;

    private LinearLayout bannerNetworkNotice;
    private TextView tvBannerText;
    private Button btnDiagnosticarRed;

    private LinearLayout layoutNetworkStatusPill;
    private View viewNetworkDot;
    private TextView tvNetworkStatusText;

    private LinearLayout layoutEmptyState;
    private Button btnReintentar;

    private ConnectivityAndInternetAccess.NetworkObserver networkObserver;
    private ConnectivityAndInternetAccess.NetworkState currentNetworkState;

    private NewsViewModel newsState;
    private DescargaNoticiasRSS activeFeedTask;
    private DescargaNoticiasRSS activePageTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        newsState = new ViewModelProvider(this).get(NewsViewModel.class);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        final View headerContainer = findViewById(R.id.headerContainer);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        rvNoticias = findViewById(R.id.rvNoticias);
        bannerNetworkNotice = findViewById(R.id.bannerNetworkNotice);
        tvBannerText = findViewById(R.id.tvBannerText);
        btnDiagnosticarRed = findViewById(R.id.btnDiagnosticarRed);

        layoutNetworkStatusPill = findViewById(R.id.layoutNetworkStatusPill);
        viewNetworkDot = findViewById(R.id.viewNetworkDot);
        tvNetworkStatusText = findViewById(R.id.tvNetworkStatusText);

        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnReintentar = findViewById(R.id.btnReintentar);

        // Evita mostrar el estado verde por defecto mientras el observador se registra.
        currentNetworkState = ConnectivityAndInternetAccess.snapshotNetworkState(this);
        actualizarInterfazEstadoRed(currentNetworkState);

        // Soporte para márgenes de ventana/cámara en smartphones tipo S25 Ultra
        final View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, new OnApplyWindowInsetsListener() {
                @Override
                public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                    int top = insets.getSystemWindowInsetTop();
                    int bottom = insets.getSystemWindowInsetBottom();
                    int left = insets.getSystemWindowInsetLeft();
                    int right = insets.getSystemWindowInsetRight();

                    if (headerContainer != null && top > 0) {
                        headerContainer.setPadding(left, top, right, 0);
                    }
                    if (rvNoticias != null && bottom > 0) {
                        rvNoticias.setPadding(left, rvNoticias.getPaddingTop(), right, bottom + 12);
                    }
                    return insets;
                }
            });
        }

        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvNoticias.setLayoutManager(layoutManager);
        adapter = new NoticiasAdapter(this, new ArrayList<NoticiaRSS>(), new NoticiasAdapter.OnNoticiaClickListener() {
            @Override
            public void onNoticiaClick(NoticiaRSS noticia) {
                if (noticia != null && noticia.getEnlace() != null) {
                    Intent intent = new Intent(MainActivity.this, DetalleActivity.class);
                    intent.putExtra(DetalleActivity.EXTRA_URL, noticia.getEnlace());
                    intent.putExtra(DetalleActivity.EXTRA_TITULO, noticia.getTitulo());
                    startActivity(intent);
                }
            }
        });
        rvNoticias.setAdapter(adapter);

        // Infinite scroll: cuando el usuario se aproxima al final se solicita la
        // siguiente página del feed oficial, que contiene noticias más antiguas.
        rvNoticias.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || newsState.isLoadingMore || !newsState.hasMoreNews || adapter == null) {
                    return;
                }

                int totalItems = adapter.getItemCount();
                int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                if (totalItems > 0 && lastVisibleItem >= totalItems - 1 - LOAD_MORE_THRESHOLD) {
                    cargarMasNoticias();
                }
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                ejecutarDescargarNoticias();
            }
        });

        btnReintentar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ejecutarDescargarNoticias();
            }
        });

        View.OnClickListener listenerDiagnostico = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ejecutarDiagnosticoRedCompleto();
            }
        };
        layoutNetworkStatusPill.setOnClickListener(listenerDiagnostico);
        btnDiagnosticarRed.setOnClickListener(listenerDiagnostico);

        // Cargar noticias iniciales (intenta descargar o usa caché offline)
        cargarNoticiasIniciales();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Iniciar el observador pasivo de conectividad basado en el Gist
        networkObserver = ConnectivityAndInternetAccess.observeNetwork(this, new ConnectivityAndInternetAccess.NetworkStateCallback() {
            @Override
            public void onStateChanged(ConnectivityAndInternetAccess.NetworkState state) {
                currentNetworkState = state;
                actualizarInterfazEstadoRed(state);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (networkObserver != null) {
            networkObserver.close();
            networkObserver = null;
        }
    }

    private void actualizarInterfazEstadoRed(ConnectivityAndInternetAccess.NetworkState state) {
        // El snapshot pasivo recibido del observador es la fuente de verdad para la UI.
        boolean isConnected = state != null && state.isConnected()
                && ConnectivityAndInternetAccess.hasPhysicalNetwork(this);
        boolean isWifi = ConnectivityAndInternetAccess.isConnectedWifi(this);
        boolean isMobile = ConnectivityAndInternetAccess.isConnectedMobile(this);
        boolean isVpn = ConnectivityAndInternetAccess.vpnActive(this);
        boolean isAirplane = ConnectivityAndInternetAccess.isAirplaneModeOn(this);
        boolean isFast = ConnectivityAndInternetAccess.isConnectedFast(this);
        boolean isCaptive = state != null && state.isCaptivePortalDetected();
        boolean isValidated = state != null && state.isInternetValidated();

        Log.d(TAG, "Estado observado: Connected=" + isConnected +
                ", Wifi=" + isWifi + ", Mobile=" + isMobile +
                ", VPN=" + isVpn + ", Airplane=" + isAirplane + ", Fast=" + isFast);

        if (!isConnected) {
            // Disconnected / Offline
            swipeRefreshLayout.setRefreshing(false);
            newsState.isLoadingMore = false;
            actualizarFondoPill(R.color.status_offline_bg);
            viewNetworkDot.setBackgroundResource(R.color.status_offline);
            tvNetworkStatusText.setText(isAirplane ? getString(R.string.status_airplane) : getString(R.string.status_offline));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_offline));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_offline_bg);
            tvBannerText.setText(isAirplane ?
                    getString(R.string.banner_airplane) :
                    getString(R.string.banner_offline));
        } else if (isCaptive) {
            // Captive Portal
            actualizarFondoPill(R.color.status_warning_bg);
            viewNetworkDot.setBackgroundResource(R.color.status_warning);
            tvNetworkStatusText.setText(getString(R.string.status_captive));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_warning));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_warning_bg);
            tvBannerText.setText(getString(R.string.banner_captive));
        } else if (!isValidated && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Connected without validated internet
            actualizarFondoPill(R.color.status_warning_bg);
            viewNetworkDot.setBackgroundResource(R.color.status_warning);
            tvNetworkStatusText.setText(getString(R.string.status_connecting));
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_warning));

            bannerNetworkNotice.setVisibility(View.VISIBLE);
            bannerNetworkNotice.setBackgroundResource(R.color.status_warning_bg);
            tvBannerText.setText(getString(R.string.banner_unvalidated));
        } else {
            // Fully connected & validated
            actualizarFondoPill(R.color.status_online_bg);
            viewNetworkDot.setBackgroundResource(R.color.status_online);

            String statusType = getString(R.string.status_online);
            if (isVpn) {
                statusType = getString(R.string.status_online_vpn);
            } else if (isWifi) {
                statusType = getString(R.string.status_online_wifi);
            } else if (isMobile) {
                statusType = isFast ? getString(R.string.status_online_fast) : getString(R.string.status_online_mobile);
            }
            tvNetworkStatusText.setText(statusType);
            tvNetworkStatusText.setTextColor(getResources().getColor(R.color.status_online));

            bannerNetworkNotice.setVisibility(View.GONE);
        }
    }

    private void cargarNoticiasIniciales() {
        // El observable del ViewModel no retiene la Activity destruida.
        newsState.getCacheRevision().observe(this, revision -> {
            if (!newsState.hasFreshFeed) mostrarNoticiasRetenidas();
        });
        if (!newsState.noticiasDisponibles.isEmpty()) {
            // Al rotar, restauramos inmediatamente el listado y su paginación.
            mostrarNoticiasRetenidas();
            if (!newsState.hasFreshFeed) ejecutarDescargarNoticias();
        } else {
            // Caché y feed se leen independientemente, sin bloqueos de UI.
            newsState.loadCacheOnce(NEWS_PAGE_SIZE);
            ejecutarDescargarNoticias();
        }
    }

    private void mostrarNoticiasRetenidas() {
        if (newsState.noticiasDisponibles.isEmpty()) {
            rvNoticias.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            return;
        }
        int end = Math.min(newsState.noticiasVisibles, newsState.noticiasDisponibles.size());
        adapter.updateData(new ArrayList<>(newsState.noticiasDisponibles.subList(0, end)));
        rvNoticias.setVisibility(View.VISIBLE);
        layoutEmptyState.setVisibility(View.GONE);
    }

    private void ejecutarDescargarNoticias() {
        // Guard barato; la petición RSS real es la prueba definitiva del feed.
        if (!ConnectivityRequestPolicy.canStartRemoteRequest(
                hayRedUtilizableParaNuevaPeticion())) {
            usarNoticiasOffline();
            return;
        }

        swipeRefreshLayout.setRefreshing(true);
        activeFeedTask = new DescargaNoticiasRSS(MainActivity.this, MainActivity.this, false);
        activeFeedTask.execute(RSS_URL, NoticiaRSS.RSS_MUY_INTERESANTE);
    }

    /**
     * Solicita una página adicional del feed oficial sin bloquear la interfaz con
     * un diálogo modal. Las páginas se acumulan en el adapter y se deduplican.
     */
    private void cargarMasNoticias() {
        if (newsState.isLoadingMore || !newsState.hasMoreNews || adapter == null) {
            return;
        }

        // El RSS oficial ya entrega un lote amplio. Lo mostramos por páginas para
        // que el usuario pueda recorrerlo de forma incremental sin repetir tráfico
        // ni depender de una paginación que el endpoint no expone.
        if (newsState.noticiasVisibles < newsState.noticiasDisponibles.size()) {
            int nuevoLimite = Math.min(newsState.noticiasVisibles + NEWS_PAGE_SIZE, newsState.noticiasDisponibles.size());
            adapter.appendData(new ArrayList<>(newsState.noticiasDisponibles.subList(newsState.noticiasVisibles, nuevoLimite)));
            newsState.noticiasVisibles = nuevoLimite;
            newsState.hasMoreNews = newsState.noticiasVisibles < newsState.noticiasDisponibles.size();
            return;
        }

        if (!ConnectivityRequestPolicy.canStartRemoteRequest(
                hayRedUtilizableParaNuevaPeticion())) {
            Log.d(TAG, "No se cargan más noticias: sin conexión disponible.");
            return;
        }

        newsState.isLoadingMore = true;
        final int pageToLoad = newsState.nextArchivePage;
        Log.d(TAG, "Solicitando noticias antiguas. Página RSS: " + pageToLoad);

        activePageTask = new DescargaNoticiasRSS(MainActivity.this, new iNoticiaRSS() {
                    @Override
                    public void onRecibeNoticiasRSS(ArrayList<NoticiaRSS> listaNoticias) {
                        newsState.isLoadingMore = false;

                        if (listaNoticias == null) {
                            // Error transitorio: no avanzamos de página para poder reintentarlo.
                            Log.w(TAG, "Error descargando la página RSS " + pageToLoad + ". Se reintentará al volver al final.");
                            return;
                        }

                        if (listaNoticias.isEmpty()) {
                            newsState.hasMoreNews = false;
                            Log.d(TAG, "Fin del archivo RSS alcanzado en la página " + pageToLoad);
                            Toast.makeText(MainActivity.this, getString(R.string.no_more_news), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        int added = adapter.appendData(listaNoticias);
                        newsState.nextArchivePage = pageToLoad + 1;

                        if (added > 0) {
                            newsState.consecutiveDuplicatePages = 0;
                            newsState.noticiasDisponibles.clear();
                            newsState.noticiasDisponibles.addAll(adapter.getAllData());
                            newsState.noticiasVisibles = newsState.noticiasDisponibles.size();
                            newsState.saveCache(newsState.noticiasDisponibles);
                            Log.d(TAG, "Página " + pageToLoad + " cargada: " + added + " noticias nuevas (" + listaNoticias.size() + " recibidas).");
                        } else {
                            newsState.consecutiveDuplicatePages++;
                            Log.d(TAG, "Página " + pageToLoad + " sin noticias nuevas tras deduplicar.");

                            // Algunos feeds pueden repetir una página al cambiar su contenido.
                            // Saltamos como máximo un pequeño número de páginas para evitar un bucle infinito.
                            if (newsState.consecutiveDuplicatePages >= MAX_CONSECUTIVE_DUPLICATE_PAGES) {
                                newsState.hasMoreNews = false;
                                Log.w(TAG, "Se detiene la paginación tras varias páginas consecutivas duplicadas.");
                            } else {
                                rvNoticias.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        cargarMasNoticias();
                                    }
                                });
                            }
                        }
                    }
                }, false);
        activePageTask.execute(RSS_PAGE_URL + pageToLoad, NoticiaRSS.RSS_MUY_INTERESANTE);
    }

    private void usarNoticiasOffline() {
        mostrarNoticiasRetenidas();
        newsState.loadCacheOnce(NEWS_PAGE_SIZE);
    }

    @Override
    public void onRecibeNoticiasRSS(ArrayList<NoticiaRSS> listaNoticias) {
        swipeRefreshLayout.setRefreshing(false);

        if (listaNoticias != null && !listaNoticias.isEmpty()) {
            newsState.noticiasDisponibles.clear();
            newsState.noticiasDisponibles.addAll(listaNoticias);
            newsState.noticiasVisibles = Math.min(NEWS_PAGE_SIZE, newsState.noticiasDisponibles.size());
            adapter.updateData(new ArrayList<>(newsState.noticiasDisponibles.subList(0, newsState.noticiasVisibles)));
            newsState.hasFreshFeed = true;
            newsState.saveCache(newsState.noticiasDisponibles);
            layoutEmptyState.setVisibility(View.GONE);
            rvNoticias.setVisibility(View.VISIBLE);

            // Una actualización completa reinicia el recorrido del archivo.
            newsState.nextArchivePage = 2;
            newsState.hasMoreNews = newsState.noticiasVisibles < newsState.noticiasDisponibles.size();
            newsState.isLoadingMore = false;
            newsState.consecutiveDuplicatePages = 0;

            Log.d(TAG, "Noticias recibidas con éxito: " + listaNoticias.size());
        } else {
            Toast.makeText(this, getString(R.string.rss_error), Toast.LENGTH_SHORT).show();
            usarNoticiasOffline();
        }
    }

    @Override
    protected void onDestroy() {
        if (activeFeedTask != null) activeFeedTask.cancel(true);
        if (activePageTask != null) activePageTask.cancel(true);
        newsState.isLoadingMore = false;
        super.onDestroy();
    }

    @Override
    public void onError(DescargaNoticiasRSS.FetchError error) {
        swipeRefreshLayout.setRefreshing(false);
        boolean pagination = newsState.isLoadingMore;
        newsState.isLoadingMore = false;

        if (error != null && error.getKind() == DescargaNoticiasRSS.FailureKind.OFFLINE) {
            usarNoticiasOffline();
            return;
        }

        // Si la red desapareció durante la petición, no lo presentamos como
        // un fallo del feed ni lanzamos otro diagnóstico/toast.
        if (!hayRedUtilizableParaNuevaPeticion()
                && (error == null
                || error.getKind() != DescargaNoticiasRSS.FailureKind.HTTP_STATUS)) {
            usarNoticiasOffline();
            return;
        }

        if (error != null && ConnectivityRequestPolicy.shouldDiagnoseAfterFailure(
                false, error.requiresGeneralDiagnostic())) {
            ConnectivityAndInternetAccess.checkHttpOnlyAsyncDefault(this,
                    new ConnectivityAndInternetAccess.InternetCallback() {
                        @Override
                        public void onResult(ConnectivityAndInternetAccess.InternetResult result) {
                            boolean generalInternet = result != null && result.isReachable();
                            String message = ConnectivityRequestPolicy.classifyFeedFailure(generalInternet);
                            Log.w(TAG, message, error.getCause());
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            if (!pagination) {
                                usarNoticiasOffline();
                            }
                        }
                    });
            return;
        }

        String message = getString(R.string.feed_error);
        Log.w(TAG, message + " kind=" + (error != null ? error.getKind() : "unknown"));
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (!pagination) {
            usarNoticiasOffline();
        }
    }

    private void actualizarFondoPill(int colorResId) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(getResources().getColor(colorResId));
        background.setCornerRadius(16 * getResources().getDisplayMetrics().density);
        layoutNetworkStatusPill.setBackground(background);
    }

    private boolean hayRedUtilizableParaNuevaPeticion() {
        ConnectivityAndInternetAccess.NetworkState observed = currentNetworkState;
        return ConnectivityAndInternetAccess.isConnected(this)
                && ConnectivityAndInternetAccess.hasPhysicalNetwork(this)
                && (observed == null || observed.isConnected());
    }

    private void ejecutarDiagnosticoRedCompleto() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.diag_title))
                .setMessage(getString(R.string.diag_progress))
                .setPositiveButton(getString(R.string.close), null)
                .show();

        // Chequeos estáticos rápidos de ConnectivityAndInternetAccess
        boolean isConnected = hayRedUtilizableParaNuevaPeticion();
        boolean isConnectedOrConnecting = isConnected;
        boolean hasPhysicalNetwork = ConnectivityAndInternetAccess.hasPhysicalNetwork(this);
        boolean isWifi = hasPhysicalNetwork && ConnectivityAndInternetAccess.isConnectedWifi(this);
        boolean isMobile = hasPhysicalNetwork && ConnectivityAndInternetAccess.isConnectedMobile(this);
        boolean isFast = hasPhysicalNetwork && ConnectivityAndInternetAccess.isConnectedFast(this);
        boolean isVpn = ConnectivityAndInternetAccess.vpnActive(this);
        boolean isAirplane = ConnectivityAndInternetAccess.isAirplaneModeOn(this);

        // Sondeo activo DNS/HTTP
        ConnectivityAndInternetAccess.checkInternetAsyncDefault(this, new ConnectivityAndInternetAccess.InternetCallback() {
            @Override
            public void onResult(ConnectivityAndInternetAccess.InternetResult result) {
                if (dialog != null && dialog.isShowing()) {
                    boolean reachable = result != null && result.isReachable();
                    String reachedHost = result != null ? result.getReachedHost() : getString(R.string.diag_none);
                    long time = result != null ? result.getElapsedMilliseconds() : 0;

                    String networkLabel = isConnected ? getString(R.string.diag_connected)
                            : (isConnectedOrConnecting ? getString(R.string.status_connecting)
                            : getString(R.string.diag_disconnected));
                    String typeLabel = isWifi ? getString(R.string.diag_wifi)
                            : (isMobile ? getString(R.string.diag_mobile) : getString(R.string.diag_other));
                    String speedLabel = isFast ? getString(R.string.diag_fast)
                            : getString(R.string.diag_slow);
                    String vpnLabel = isVpn ? getString(R.string.diag_yes) : getString(R.string.diag_no);
                    String airplaneLabel = isAirplane ? getString(R.string.diag_enabled)
                            : getString(R.string.diag_disabled);
                    String internetLabel = reachable ? getString(R.string.diag_internet_yes)
                            : getString(R.string.diag_internet_no);
                    dialog.setMessage(getString(R.string.diag_report,
                            networkLabel, typeLabel, speedLabel, vpnLabel, airplaneLabel,
                            internetLabel, reachedHost, time,
                            currentNetworkState != null ? currentNetworkState.toString()
                                    : getString(R.string.diag_unavailable)));

                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_buscar);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint(getString(R.string.search_hint));
                if (!newsState.searchQuery.isEmpty()) {
                    searchItem.expandActionView();
                    searchView.setQuery(newsState.searchQuery, false);
                    adapter.filter(newsState.searchQuery);
                }
                searchView.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
                searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                    @Override public boolean onMenuItemActionExpand(MenuItem item) {
                        searchView.post(new Runnable() {
                            @Override public void run() {
                                searchView.setIconified(false);
                                searchView.requestFocus();
                                android.view.inputmethod.InputMethodManager ime =
                                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                if (ime != null) {
                                    View focused = searchView.findFocus();
                                    ime.showSoftInput(focused != null ? focused : searchView,
                                            android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                }
                            }
                        });
                        return true;
                    }
                    @Override public boolean onMenuItemActionCollapse(MenuItem item) {
                        searchView.setQuery("", false);
                        newsState.searchQuery = "";
                        if (adapter != null) adapter.filter("");
                        return true;
                    }
                });
                
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        if (adapter != null) {
                            newsState.searchQuery = query;
                            adapter.filter(query);
                        }
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        if (adapter != null) {
                            newsState.searchQuery = newText;
                            adapter.filter(newText);
                        }
                        return true;
                    }
                });
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_actualizar) {
            ejecutarDescargarNoticias();
            return true;
        } else if (id == R.id.action_test_conectividad) {
            ejecutarDiagnosticoRedCompleto();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
