package com.cognitiva.sconnect;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.util.Log;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import org.apache.cordova.*;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;

public class SConnectActivity extends DroidGap {
	AuthenticationToken token = new AuthenticationToken();
	String url;
	String server;
	String port;
	String lastusername;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	
        super.onCreate(savedInstanceState);
        buildConnectionUrl();
        lastusername = readLastUsername();
        super.setContentView(R.layout.main);
        if (lastusername != "") {
        	EditText usernameField = (EditText)findViewById(R.id.username);
        	usernameField.setText(lastusername);
        }
    }

    public String readLastUsername() {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	return sp.getString("lastusername", "");
    }
    
    public void setLastUsername(String username) {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	SharedPreferences.Editor editor = sp.edit();
    	editor.putString("lastusername", username);
    	editor.commit();
    }
    
    /** Builds connection URL from settings */
    public void buildConnectionUrl() {
    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    	String server = sp.getString("server", "");
    	String port = sp.getString("port", "");
    	String path = sp.getString("path", "");
    	String client = sp.getString("client", "");
    	Boolean https = sp.getBoolean("https", false);
    	this.server = server;
    	this.port = port;
    	if (server == "" || port == "" || path == "" || client == "") {
    		return;
    	}
    	if (https) {
        	url = "https://";
    	} else {
    		url = "http://";
    	}
    	url += server;
    	if (port != "80") {
    		url += ":" + port;
    	}
    	url += path;
    	url += "?sap-client=" + client;
    	Log.v("Connection URL", url);
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.menu, menu);
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	boolean handled = false;

        switch(item.getItemId()) {
        	case R.id.quit:
        		this.endActivity();
        		handled = true;
        		break;
        	case R.id.settings:
        		startActivity(new Intent(this, AppPreferencesActivity.class));
        		handled = true;
        	default:
        		break;
        }
        return handled;
    }
    
    public void onClickLogin(View view) {  
    	buildConnectionUrl();
    	if (url == "") {
    		new AlertDialog.Builder(this).setTitle("Missing Configuration")
			.setMessage("Connection information is missing in the settings.")
			.setPositiveButton("OK", new OnClickListener() {
				public void onClick(DialogInterface dialog, int arg1) {
					dialog.dismiss();
				}})
			.show();
    	} else {
    	
    	EditText usernameField = (EditText)findViewById(R.id.username);
    	EditText passwordField = (EditText)findViewById(R.id.password);
    	String username = usernameField.getText().toString();
    	if (username != lastusername) {
    		setLastUsername(username);
    	}
    	
    	//hide the keyboard
    	InputMethodManager mgr=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
    	mgr.hideSoftInputFromWindow(usernameField.getWindowToken(), 0);
    	mgr.hideSoftInputFromWindow(passwordField.getWindowToken(), 0);
    	
    	token.setUserName(username);
    	token.setPassword(passwordField.getText().toString());
    	super.setAuthenticationToken(token,"", "");
    	if (checkAuthentication(token.getUserName(),token.getPassword())) {
    		super.setIntegerProperty("splashscreen", R.drawable.splash);
    		super.loadUrl(url);
    	} else {
    		new AlertDialog.Builder(this).setTitle("Failed Authentication")
    				.setMessage("The username/password combination is not valid")
    				.setPositiveButton("OK", new OnClickListener() {
    					public void onClick(DialogInterface dialog, int arg1) {
    						dialog.dismiss();
    					}})
    				.show();
    	}
    	}
    }
    
    boolean checkAuthentication(String username, String password) {
    	
    	int port_number = Integer.parseInt( port );
    	
    	if (username.length() == 0 || password.length() == 0) {
    		return false;
    	}
    	
    	HttpResponse response;
    	DefaultHttpClient httpclient;
    	
    	String packageName = getPackageName();
    	PackageManager pm = getPackageManager();
    	try {
    		ApplicationInfo appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            if ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            	// only in debug mode the HTTP client accepts any certificate
            	httpclient = getNewHttpClient();
            } else {
            	httpclient = new DefaultHttpClient();
            }
    	} catch (NameNotFoundException e) {
    		httpclient = new DefaultHttpClient();
    	}
    	
    	try {
    		httpclient.getCredentialsProvider().setCredentials(
                    new AuthScope(server, port_number),
                    new UsernamePasswordCredentials(username, password));
    		HttpGet httpget = new HttpGet(url);
    		response = httpclient.execute(httpget);
    		Log.v("HTTP response", response.getStatusLine().toString());
    		if (response.getStatusLine().getStatusCode() == 200) {
    			return true;
    		} else {
    			return false;
    		}
    	} catch (ClientProtocolException e) {
    		Log.v("Client protocol exception", e.toString());
    	} catch (IOException e) {
    		Log.v("Client protocol exception", e.toString());
    	} finally {
    		httpclient.getConnectionManager().shutdown();
    	}
      	
    	return false;
    }
    
    // should not be needed with the current way to start the app
    boolean isUrlWhiteListed(String url) {
    	return true;
    }

    /* this http client follows the phonegap logic of accepting SSL certificates 
     * when in debug mode.
     */
    public DefaultHttpClient getNewHttpClient() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
            sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpParams params = new BasicHttpParams();
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

            SchemeRegistry registry = new SchemeRegistry();
            registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            registry.register(new Scheme("https", sf, 443));

            ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

            return new DefaultHttpClient(ccm, params);
        } catch (Exception e) {
            return new DefaultHttpClient();
        }
    }
    
}

class MySSLSocketFactory extends SSLSocketFactory {
    SSLContext sslContext = SSLContext.getInstance("TLS");

    public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
        super(truststore);

        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        sslContext.init(null, new TrustManager[] { tm }, null);
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
    }

    @Override
    public Socket createSocket() throws IOException {
        return sslContext.getSocketFactory().createSocket();
    }
}
