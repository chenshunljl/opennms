/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2013-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.geocoder.nominatim;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.opennms.core.web.HttpClientWrapper;
import org.opennms.features.geocoder.Coordinates;
import org.opennms.features.geocoder.GeocoderException;
import org.opennms.features.geocoder.GeocoderResult;
import org.opennms.features.geocoder.GeocoderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class NominatimGeocoderService implements GeocoderService {

    private String referer;
    private String urlTemplate;
    private String userAgent;
    private String emailAddress;
    private boolean useSystemProxy;
    private boolean acceptUsageTerms;

    private Logger m_log = LoggerFactory.getLogger(getClass());

    public NominatimGeocoderService() {

    }

    @Override
    public String getId() {
        return "nominatim";
    }

    @Override
    public GeocoderResult resolveAddress(final String address) throws GeocoderException {
        if (!acceptUsageTerms) {
            return new GeocoderResult(new GeocoderException("Cannot resolve coordinates. Usage Terms must be accepted before."));
        }
        try (HttpClientWrapper clientWrapper = HttpClientWrapper.create().dontReuseConnections()) {
            if (useSystemProxy) {
                clientWrapper.useSystemProxySettings();
            }

            final String url = buildUrl(emailAddress, address);
            final HttpUriRequest method = new HttpGet(url);
            if (!Strings.isNullOrEmpty(userAgent)) {
                method.addHeader("User-Agent", userAgent);
            }
            if (!Strings.isNullOrEmpty(referer)) {
                method.addHeader("Referer", referer);
            }

            try (CloseableHttpResponse response = clientWrapper.execute(method)) {
                final StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() != 200) {
                    throw new GeocoderException("Nominatim returned a non-OK response code: " + statusLine.getStatusCode() + " " + statusLine.getReasonPhrase());
                }
                final InputStream responseStream = response.getEntity().getContent();
                final JSONTokener tokener = new JSONTokener(responseStream);
                final JSONArray results = new JSONArray(tokener);
                if (results.length() > 0) {
                    final JSONObject result = results.getJSONObject(0);
                    if (result.has("lat") && result.has("lon")) {
                        final Float longitude = result.getFloat("lon");
                        final Float latitude = result.getFloat("lat");
                        return new GeocoderResult(new Coordinates(longitude, latitude));
                    }
                }
                return new GeocoderResult(new GeocoderException("No results found for address " + address));
            }
        } catch (IOException e) {
            return new GeocoderResult(e);
        }
    }

    @Override
    public Map<String, Object> getProperties() {
        final Map<String, Object> properties = new HashMap<>();
        properties.put("email", emailAddress);
        properties.put("referer", referer);
        properties.put("userAgent", userAgent);
        properties.put("useSystemProxy", useSystemProxy);
        properties.put("url", urlTemplate);
        properties.put("acceptUsageTerms", acceptUsageTerms);
        return properties;
    }

    private String buildUrl(final String emailAddress, final String addressToResolve) throws UnsupportedEncodingException {
        Objects.requireNonNull(emailAddress);
        Objects.requireNonNull(addressToResolve);

        final String url = urlTemplate.replaceAll("\\{email\\}", URLEncoder.encode(emailAddress, "UTF-8"))
                                      .replaceAll("\\{query\\}", URLEncoder.encode(addressToResolve, "UTF-8"));
        return url;
    }

    public void setReferer(String referer) {
        this.referer = referer;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public void setUseSystemProxy(boolean useSystemProxy) {
        this.useSystemProxy = useSystemProxy;
    }

    public void setAcceptUsageTerms(boolean acceptUsageTerms) {
        this.acceptUsageTerms = acceptUsageTerms;
    }
}
