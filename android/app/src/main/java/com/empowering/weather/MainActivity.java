package com.empowering.weather;

import android.os.Bundle;
import android.webkit.CookieManager;
import android.os.Build;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Ensure the WebView accepts cookies. For API >= 21 also allow third-party cookies
		try {
			CookieManager cookieManager = CookieManager.getInstance();
			cookieManager.setAcceptCookie(true);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				// BridgeActivity hosts the WebView used by Capacitor; getWebView() is available
				try {
					android.webkit.WebView webView = (android.webkit.WebView) getBridge().getWebView();
					cookieManager.setAcceptThirdPartyCookies(webView, true);
				} catch (Throwable ignored) {
					// Fallback: enable global third-party cookies if available
					try { cookieManager.setAcceptThirdPartyCookies(new android.webkit.WebView(this), true); } catch (Throwable ignored2) {}
				}
			}
		} catch (Throwable ignored) {}
	}
}
