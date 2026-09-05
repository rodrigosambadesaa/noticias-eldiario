package com.example.muyinteresanteNoTocar;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import java.util.ArrayList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import com.example.muyinteresante.util.ConnectivityAndInternetAccess;

/* Parsea un canal RSS y devuelve sus items en un ArrayList */

public class DescargaNoticiasRSS extends AsyncTask<String,Integer,ArrayList<NoticiaRSS>>{
	public enum FailureKind { OFFLINE, AMBIGUOUS_CONNECTIVITY, HTTP_STATUS, PARSE, UNKNOWN }

	public static final class FetchError {
		private final FailureKind kind;
		private final int httpStatus;
		private final Throwable cause;

		FetchError(FailureKind kind, int httpStatus, Throwable cause) {
			this.kind = kind;
			this.httpStatus = httpStatus;
			this.cause = cause;
		}

		public FailureKind getKind() { return kind; }
		public int getHttpStatus() { return httpStatus; }
		public Throwable getCause() { return cause; }
		public boolean requiresGeneralDiagnostic() {
			return kind == FailureKind.AMBIGUOUS_CONNECTIVITY;
		}
	}

	private Context contexto=null;
	private iNoticiaRSS objetoReceptor=null;
	private ProgressDialog pd=null;
	private boolean mostrarProgreso=true;
	private boolean remoteSkippedOffline=false;
	private boolean connectionAttemptStarted=false;
	private FetchError fetchError;
	
	private static final String MENSAJE_PD="Descargando noticias...";
	
	
	public DescargaNoticiasRSS(Context contexto, iNoticiaRSS objetoReceptor){
		this(contexto, objetoReceptor, true);
	}

	/**
	 * Permite reutilizar el descargador para paginación/infinite scroll sin abrir
	 * un ProgressDialog modal cada vez que se solicitan noticias antiguas.
	 */
	public DescargaNoticiasRSS(Context contexto, iNoticiaRSS objetoReceptor, boolean mostrarProgreso){
		this.contexto = contexto;
		this.objetoReceptor = objetoReceptor;
		this.mostrarProgreso = mostrarProgreso;
	}


	@Override
	protected void onPreExecute() {
		super.onPreExecute();
		
		if (contexto != null && !ConnectivityAndInternetAccess.isConnected(contexto)) {
			remoteSkippedOffline = true;
			fetchError = new FetchError(FailureKind.OFFLINE, 0, null);
		} else if (contexto != null) {
			// Registramos inicio de intento de conexión para seguimiento de estado
			ConnectivityAndInternetAccess.beginConnectionAttempt(contexto);
			connectionAttemptStarted = true;
		}
		
		if (mostrarProgreso && contexto != null) {
			pd = new ProgressDialog(contexto);
			pd.setMessage(MENSAJE_PD);
			pd.setCancelable(true);
			pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					DescargaNoticiasRSS.this.cancel(true);
				}
			});
			
			pd.show();
		}
	}

	
	@Override
	protected void onCancelled() {
		super.onCancelled();
		
		// Finalizamos intento de conexión
		if (connectionAttemptStarted) {
			ConnectivityAndInternetAccess.endConnectionAttempt();
			connectionAttemptStarted = false;
		}
		
		if (pd!=null) pd.dismiss();
	}
	
	 
	@Override							// Recibe URL y nombre Canal RSS.
	protected ArrayList<NoticiaRSS> doInBackground(String... params) {
		
		InputStream entrada = null;
		
		try{
			if (remoteSkippedOffline || (contexto != null && !ConnectivityAndInternetAccess.isConnected(contexto))) {
				fetchError = new FetchError(FailureKind.OFFLINE, 0, null);
				Log.w("DescargaNoticiasRSS", "Descarga cancelada: Dispositivo sin conexión según ConnectivityAndInternetAccess.");
				return null;
			}

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setIgnoringComments(true);
			dbf.setCoalescing(true);
			DocumentBuilder db = dbf.newDocumentBuilder(); 
			
			 // Creamos objeto URL a partir de la direccion web para conectarnos con el servidor
			URL url = new URL(params[0]);
			URLConnection conex = url.openConnection(); // Abrimos la conexion
			conex.setConnectTimeout(10000);
			conex.setReadTimeout(10000);
			conex.setUseCaches(false); // Evitamos la cache de datos.
			conex.setRequestProperty("accept", "application/rss+xml, application/xml, text/xml, */*");
			conex.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) noticias-eldiario/1.0");
			 
			 // Abrimos el fichero para su lectura/descarga
			if (conex instanceof HttpURLConnection) {
				HttpURLConnection http = (HttpURLConnection) conex;
				http.setInstanceFollowRedirects(true);
				int status = http.getResponseCode();
				if (status < 200 || status >= 300) {
					fetchError = new FetchError(FailureKind.HTTP_STATUS, status, null);
					http.disconnect();
					return null;
				}
			}
			entrada = conex.getInputStream();	

			Document arbolXML =db.parse(entrada);
			entrada.close();
			Element raiz = arbolXML.getDocumentElement(); 
			raiz.normalize(); 
			
			ArrayList<NoticiaRSS> noticias = new ArrayList<NoticiaRSS>();
			
			NodeList listaItems = raiz.getElementsByTagName("item");
			
			for (int i=0;i<listaItems.getLength();i++){
				try {
					Element item = (Element)listaItems.item(i);
					noticias.add(new NoticiaRSS(item, params[1]));
					
					publishProgress(noticias.size());
				}
				catch(Exception e){ e.printStackTrace();}
			}
			
			return noticias;
		}
		catch (Exception e){
			if (isAmbiguousConnectivityFailure(e)) {
				fetchError = new FetchError(FailureKind.AMBIGUOUS_CONNECTIVITY, 0, e);
			} else if (e instanceof org.xml.sax.SAXException) {
				fetchError = new FetchError(FailureKind.PARSE, 0, e);
			} else {
				fetchError = new FetchError(FailureKind.UNKNOWN, 0, e);
			}
			e.printStackTrace();
			return null;
		}
		finally {
			if (entrada != null) {
				try {
					entrada.close();
				} catch (Exception ignored) { }
			}
		}

	}
	
	
	@Override
	protected void onPostExecute(ArrayList<NoticiaRSS> result) {
		super.onPostExecute(result);
		
		// Finalizamos intento de conexión
		if (connectionAttemptStarted) {
			ConnectivityAndInternetAccess.endConnectionAttempt();
			connectionAttemptStarted = false;
		}
		
		if (pd!=null) pd.dismiss();
		if (objetoReceptor!=null ) {
			if (fetchError != null) {
				objetoReceptor.onError(fetchError);
			} else {
				objetoReceptor.onRecibeNoticiasRSS(result);
			}
		}
	}

	private boolean isAmbiguousConnectivityFailure(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof UnknownHostException
					|| current instanceof ConnectException
					|| current instanceof SocketTimeoutException
					|| current instanceof SSLException
					|| current instanceof SocketException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}


	@Override
	protected void onProgressUpdate(Integer... values) {
		super.onProgressUpdate(values);
		if (pd != null && values != null && values.length > 0) {
			pd.setMessage(MENSAJE_PD + " " + values[0]);
		}
	}
}
