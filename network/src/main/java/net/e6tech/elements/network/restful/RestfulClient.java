/*
Copyright 2015 Futeh Kao

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package net.e6tech.elements.network.restful;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.e6tech.elements.common.inject.Inject;
import net.e6tech.elements.common.logging.Logger;
import net.e6tech.elements.common.serialization.ObjectMapperFactory;
import net.e6tech.elements.common.util.ErrorResponse;
import net.e6tech.elements.common.util.ExceptionMapper;
import net.e6tech.elements.common.util.SystemException;
import net.e6tech.elements.security.JCEKS;

import javax.net.ssl.*;
import javax.ws.rs.*;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;

/**
 * Created by futeh.
 */
@SuppressWarnings("squid:S3776")
public class RestfulClient {

    private static Logger logger = Logger.getLogger();
    private static final X509Certificate[] EMPTY_CERTIFICATES = new X509Certificate[0];

    public static final ObjectMapper mapper = ObjectMapperFactory.newInstance();

    private ExceptionMapper exceptionMapper;
    private String staticAddress;
    private String encoding = "UTF-8";
    private String trustStore;
    private boolean skipHostnameCheck = false;
    private boolean skipCertCheck = false;
    private SSLSocketFactory sslSocketFactory;
    private int connectionTimeout = -1;
    private int readTimeout = -1;
    private PrintWriter printer;

    public RestfulClient() {}

    public RestfulClient(String address) {
        setAddress(address);
    }

    public ExceptionMapper getExceptionMapper() {
        return exceptionMapper;
    }

    public void setExceptionMapper(ExceptionMapper exceptionMapper) {
        this.exceptionMapper = exceptionMapper;
    }

    public synchronized String getAddress() {
        return staticAddress;
    }

    public synchronized void setAddress(String path) {
        this.staticAddress = path;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        sslSocketFactory = null;
        this.trustStore = trustStore;
    }

    public boolean isSkipHostnameCheck() {
        return skipHostnameCheck;
    }

    public void setSkipHostnameCheck(boolean skipHostnameCheck) {
        sslSocketFactory = null;
        this.skipHostnameCheck = skipHostnameCheck;
    }

    public boolean isSkipCertCheck() {
        return skipCertCheck;
    }

    public void setSkipCertCheck(boolean skipCertCheck) {
        sslSocketFactory = null;
        this.skipCertCheck = skipCertCheck;
    }


    public PrintWriter getPrinter() {
        return printer;
    }

    @Inject(optional = true)
    public void setPrinter(PrintWriter printer) {
        this.printer = printer;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    @SuppressWarnings("squid:S134")
    private Param[] toParams(Object object) {
        List<Param> params = new ArrayList<>();
        if (object != null) {
            Class cls = object.getClass();
            BeanInfo beanInfo = null;
            try {
                beanInfo = Introspector.getBeanInfo(cls);
            } catch (IntrospectionException e) {
                throw new SystemException(e);
            }
            for (PropertyDescriptor desc : beanInfo.getPropertyDescriptors()) {
                if (desc.getReadMethod()!= null) {
                    try {
                        Object value = desc.getReadMethod().invoke(object);
                        if (value != null)
                            params.add(new Param(desc.getName(), value.toString()));
                    } catch (Exception e) {
                        throw new SystemException(e);
                    }
                }
            }
        }
        return params.toArray(new Param[params.size()]);
    }

    public Response get(String context, Object object) throws Throwable {
        if (object instanceof Param) {
            return get(context, new Param[] { (Param) object});
        }
        return get(context, toParams(object));
    }

    public Request create() {
        return new Request(this);
    }

    public Response get(String context, Param ... params) throws Throwable {
        return new Request(this).get(context, params);
    }

    public Response delete(String context, Param ... params) throws Throwable {
        return new Request(this).delete(context, params);
    }

    public Response put(String context, Object data, Object object) throws Throwable {
        if (object instanceof Param) {
            return put(context, data, new Param[]{(Param) object});
        }
        return put(context, data, toParams(object));
    }

    public Response put(String context, Object data,  Param ... params) throws Throwable {
        return new Request(this).put(context, data, params);
    }

    public Response post(String context, Object data, Object object) throws Throwable {
        if (object instanceof Param) {
            return post(context, data, new Param[] { (Param)object} );
        }
        return post(context, data, toParams(object));
    }

    public Response post(String context, Object data,  Param ... params) throws Throwable {
        return new Request(this).post(context, data, params);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private String constructPath(String destination, String ctx, Param ... params) {
        String dest = destination;
        String context = ctx;
        String fullPath = null;

        if (!dest.endsWith("/"))
            dest = dest + "/";

        if (context != null) {
            while (context.startsWith("/"))
                context = context.substring(1);
        }

        fullPath = dest + context;

        while (fullPath.endsWith("/"))
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        if (params !=  null) {
            StringBuilder builder = new StringBuilder();
            List<Param> list = new ArrayList<>();
            for(Param param : params)
                if (param.getValue() != null)
                    list.add(param);
            for (int i = 0; i < list.size(); i++) {
                if (i == 0)
                    builder.append("?");
                builder.append(list.get(i).encode());
                if (i != list.size() - 1)
                    builder.append("&");
            }
            fullPath = fullPath + builder.toString();
        }
        return fullPath ;
    }

    HttpURLConnection open(String dest, String context, Param ... params) throws IOException {
        String fullPath = constructPath(dest, context, params);
        URL url = null;
        try {
            logger.debug(fullPath);
            url = new URL(fullPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            if (connectionTimeout >= 0)
                conn.setConnectTimeout(connectionTimeout);
            if (readTimeout >= 0)
                conn.setReadTimeout(readTimeout);
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection https = (HttpsURLConnection) conn;
                https.setSSLSocketFactory(getSSLSocketFactory());
                if (skipHostnameCheck || skipCertCheck)
                    https.setHostnameVerifier((hostname, session) -> true);
            }
            return conn;
        } catch (MalformedURLException e) {
            throw logger.systemException(e);
        }
    }

    protected Response submit(String context, String method, Properties requestProperties, Object data, Param ... params) throws Throwable {
        return _submit(staticAddress, context, method, requestProperties, data, params);
    }

    @SuppressWarnings({"squid:MethodCyclomaticComplexity", "squid:S134", "squid:S1141", "squid:S00100", "squid:S00112", "squid:S2093"})
    protected Response _submit(String dest, String context, String method, Properties requestProperties, Object data, Param ... params) throws Throwable {
        Response response = null;
        HttpURLConnection conn = null;
        try {
            conn = open(dest, context, params);
            if (method.equals(Request.POST) || method.equals(Request.PUT)) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
            }
            conn.setRequestMethod(method);
            setConnectionProperties(conn);
            loadRequestProperties(conn, requestProperties);

            if (printer != null) {
                printer.println("REQUEST ----------------------------");
                printer.println(method + " " + constructPath(dest, context, params));
                printHeaders((Map) requestProperties);
                printHeaders(conn.getRequestProperties());
                if (data != null) {
                    printer.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
                }
                printer.println();
            }

            if (method.equals(Request.POST) || method.equals(Request.PUT)) {
                OutputStream out = conn.getOutputStream();
                if (data != null) {
                    Writer writer = new OutputStreamWriter(new BufferedOutputStream(out), "UTF-8");
                    String posted = (data instanceof String) ? (String) data : mapper.writeValueAsString(data);
                    writer.write(posted);
                    logger.debug(posted);
                    writer.flush();
                    writer.close();
                }
                out.close();
            }

            response = readResponse(conn);
            if (printer != null) {
                printer.println("RESPONSE ----------------------------");
                List<String> statusList = response.getHeaderFields().get(null);
                if (statusList != null  && !statusList.isEmpty())
                    printer.println(statusList.get(0));
                printer.println("Response Code=" + response.getResponseCode());
                printHeaders(response.getHeaderFields());
                String result = response.getResult();
                if (result != null && result.length() > 0) {
                    Object ret;
                    if (result.startsWith("[")) {
                        ret = mapper.readValue(response.getResult(), List.class);
                    } else if (result.startsWith("{")) {
                        ret = mapper.readValue(response.getResult(), Map.class);
                    } else if (result.startsWith("\"")){
                        ret = mapper.readValue(response.getResult(), String.class);
                    } else if (Character.isDigit(result.charAt(0))) {
                        if (result.contains(".")) {
                            ret = mapper.readValue(response.getResult(), BigDecimal.class);
                        } else {
                            ret = mapper.readValue(response.getResult(), Long.class);
                        }
                    } else if ("true".equalsIgnoreCase(result) || "false".equalsIgnoreCase(result)) {
                        ret = Boolean.getBoolean(result);
                    } else {
                        ret = result;
                    }
                   printer.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ret));
                }
                printer.println();
            }
            try {
                checkResponseCode(response.getResponseCode(), response.getResult());
            } catch (ClientErrorException ex) {
                Throwable mappedThrowable = null;
                String result = ex.getMessage();
                if (result != null && exceptionMapper != null) {
                    try {
                        ErrorResponse error = mapper.readValue(result, ErrorResponse.class);
                        if (error != null) {
                            mappedThrowable = exceptionMapper.fromResponse(error);
                        }
                    } catch (Exception e) {
                        Logger.suppress(e);
                    }
                }
                if (mappedThrowable != null)
                    throw mappedThrowable;
                else throw ex;
            }
        } catch (MalformedURLException e) {
            logger.systemException(e);
        } finally {
            if (conn != null)
                conn.disconnect();
        }

        return response;
    }

    private Response readResponse(HttpURLConnection conn) throws IOException {
        Response response = new Response();

        response.setHeaderFields(conn.getHeaderFields());
        response.setResponseCode(conn.getResponseCode());

        if (conn.getResponseCode() == HTTP_NO_CONTENT)
            return response;

        InputStream in = null;
        try {
            in = conn.getInputStream();
        } catch (IOException ex) {
            Logger.suppress(ex);
            in = conn.getErrorStream();
            if (in == null)
                checkResponseCode(conn.getResponseCode(), conn.getResponseMessage());
        }

        try {
            BufferedInputStream bis = new BufferedInputStream(in);
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            int read = 0;
            int bufSize = 4096;
            byte[] buffer = new byte[bufSize];
            while (true) {
                read = bis.read(buffer);
                if (read == -1) {
                    break;
                }
                byteArray.write(buffer, 0, read);
            }
            String result = new String(byteArray.toByteArray(), encoding);
            response.setResult(result);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }

        return response;
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    private void checkResponseCode(int code, String message) {
        javax.ws.rs.core.Response.Status status = javax.ws.rs.core.Response.Status.fromStatusCode(code);
        if (code == 500)
            throw new InternalServerErrorException();
        if (code > 500)
            throw new ServiceUnavailableException();

        switch (status) {
            case OK:
            case CREATED:
            case ACCEPTED:
            case NO_CONTENT:
            case RESET_CONTENT:
            case PARTIAL_CONTENT:
                return;
            case BAD_REQUEST: throw new BadRequestException(message);
            case UNAUTHORIZED: throw new NotAuthorizedException(javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.UNAUTHORIZED).build());
            case PAYMENT_REQUIRED:
            case FORBIDDEN: throw new ForbiddenException(message);
            case NOT_FOUND: throw new NotFoundException(message);
            case METHOD_NOT_ALLOWED: throw new NotAllowedException(message,
                    javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED).build());
            case NOT_ACCEPTABLE: throw new NotAcceptableException(message);
            case UNSUPPORTED_MEDIA_TYPE: throw new NotSupportedException(message);
            default: throw new ServerErrorException(message, status);
        }
    }

    @SuppressWarnings({"squid:S135", "squid:S134"})
    private void printHeaders(Map<String, ?> headers) {
        for (Map.Entry<String, ?> entry : headers.entrySet()) {
            if (entry.getKey() == null)
                continue;
            printer.print(entry.getKey() + ": ");
            if ("Authorization".equals(entry.getKey()) && entry.getValue() instanceof String) {
                String auth = (String) entry.getValue();
                if (auth.startsWith("Bearer ")) {
                    printer.println("Bearer ...");
                    continue;
                }
            }
            boolean first = true;
            if (entry.getValue() instanceof List) {
                for (String item : (List<String>) entry.getValue()) {
                    if (first) {
                        first = false;
                    } else {
                        printer.print(", ");
                    }
                    printer.print(item);
                }
            } else {
                printer.print(entry.getValue());
            }
            printer.println();
        }
        printer.flush();
    }

    private void setConnectionProperties(HttpURLConnection conn)  {
        conn.setDoInput(true);
        conn.setUseCaches(false);
        conn.setAllowUserInteraction(false);
        conn.setRequestProperty("Accept", "application/json");
    }

    private void loadRequestProperties(HttpURLConnection conn, Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            conn.setRequestProperty(key, properties.getProperty(key));
        }
    }

    private SSLSocketFactory getSSLSocketFactory() {
        if (sslSocketFactory != null)
            return sslSocketFactory;
        TrustManager[] trustManagers = null;
        if (skipCertCheck) {
            trustManagers = new TrustManager[] { new AcceptAllTrustManager()};
        } else {
            try {
                if (trustStore != null) {
                    JCEKS jceks = new JCEKS(trustStore, null);
                    trustManagers = jceks.getTrustManagers();
                } else {
                    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    factory.init((KeyStore)null);
                    trustManagers = factory.getTrustManagers();
                }
            } catch (Exception ex) {
                throw logger.systemException(ex);
            }
        }

        SSLContext ctx = null;
        try {
            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustManagers, null);
            sslSocketFactory = ctx.getSocketFactory();
            return sslSocketFactory;
        } catch (Exception e) {
            throw logger.systemException(e);
        }
    }

    public class AcceptAllTrustManager implements X509TrustManager {

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // do nothing
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // do nothing
        }

        public X509Certificate[] getAcceptedIssuers() {
            return EMPTY_CERTIFICATES;
        }
    }
}
