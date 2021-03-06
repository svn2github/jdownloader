package org.jdownloader.api.myjdownloader;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.remoteapi.RemoteAPIRequest;
import org.appwork.remoteapi.SessionRemoteAPIRequest;
import org.appwork.remoteapi.exceptions.ApiInterfaceNotAvailable;
import org.appwork.remoteapi.exceptions.BasicRemoteAPIException;
import org.appwork.remoteapi.exceptions.InternalApiException;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging2.LogSource;
import org.appwork.utils.net.Base64OutputStream;
import org.appwork.utils.net.ChunkedOutputStream;
import org.appwork.utils.net.DeChunkingOutputStream;
import org.appwork.utils.net.HTTPHeader;
import org.appwork.utils.net.httpserver.HttpConnection;
import org.appwork.utils.net.httpserver.handler.HttpRequestHandler;
import org.appwork.utils.net.httpserver.requests.GetRequest;
import org.appwork.utils.net.httpserver.requests.HeadRequest;
import org.appwork.utils.net.httpserver.requests.HttpRequest;
import org.appwork.utils.net.httpserver.requests.OptionsRequest;
import org.appwork.utils.net.httpserver.requests.PostRequest;
import org.appwork.utils.net.httpserver.responses.HttpResponse;
import org.jdownloader.api.RemoteAPIController;
import org.jdownloader.api.myjdownloader.api.MyJDownloaderAPI;
import org.jdownloader.myjdownloader.RequestLineParser;
import org.jdownloader.myjdownloader.client.SessionInfo;
import org.jdownloader.myjdownloader.client.exceptions.MyJDownloaderException;

public class MyJDownloaderHttpConnection extends HttpConnection {
    
    protected final static ArrayList<HttpRequestHandler> requestHandler = new ArrayList<HttpRequestHandler>();
    static {
        requestHandler.add(new OptionsRequestHandler());
        requestHandler.add(RemoteAPIController.getInstance().getRequestHandler());
    }
    protected final MyJDownloaderAPI                     api;
    
    private LogSource                                    logger;
    
    public static MyJDownloaderHttpConnection getMyJDownloaderHttpConnection(RemoteAPIRequest request) {
        if (request instanceof SessionRemoteAPIRequest<?>) {
            Object session = ((SessionRemoteAPIRequest<?>) request).getSession();
            if (session != null && session instanceof MyJDownloaderAPISession) { return ((MyJDownloaderAPISession) session).getConnection(); }
        }
        return null;
    }
    
    public MyJDownloaderHttpConnection(Socket clientConnection, MyJDownloaderAPI api) throws IOException {
        super(null, clientConnection);
        this.api = api;
        logger = api.getLogger();
    }
    
    protected GetRequest buildGetRequest() {
        return new MyJDownloaderGetRequest(this);
    }
    
    protected HeadRequest buildHeadRequest() {
        return new MyJDownloaderHeadRequest(this);
    }
    
    protected OptionsRequest buildOptionsRequest() {
        return new MyJDownloaderOptionsRequest(this);
    }
    
    protected PostRequest buildPostRequest() {
        
        return new MyJDownloaderPostRequest(this);
    }
    
    private OutputStream os                     = null;
    private byte[]       payloadEncryptionToken = null;
    
    private String       requestConnectToken;
    private HTTPHeader   accept_encoding;
    
    @Override
    public List<HttpRequestHandler> getHandler() {
        return requestHandler;
    }
    
    protected void onUnhandled(final HttpRequest request, final HttpResponse response) throws IOException {
        onException(new ApiInterfaceNotAvailable(), request, response);
    }
    
    @Override
    public boolean onException(Throwable e, final HttpRequest request, final HttpResponse response) throws IOException {
        BasicRemoteAPIException apiException;
        if (!(e instanceof BasicRemoteAPIException)) {
            apiException = new InternalApiException(e);
        } else {
            apiException = (BasicRemoteAPIException) e;
        }
        logger.log(apiException);
        this.response = new HttpResponse(this);
        return apiException.handle(this.response);
    }
    
    // @Override
    // public byte[] getAESJSon_IV(String ID) {
    // if (ID != null) {
    // if (ID.equalsIgnoreCase("jd")) {
    // return Arrays.copyOfRange(payloadEncryptionToken, 0, 16);
    // } else if (ID.equalsIgnoreCase("server")) { return Arrays.copyOfRange(serverSecret, 0, 16); }
    // }
    // return null;
    // }
    
    public byte[] getPayloadEncryptionToken() {
        return payloadEncryptionToken;
    }
    
    public String getRequestConnectToken() {
        return requestConnectToken;
    }
    
    // @Override
    // public byte[] getAESJSon_KEY(String ID) {
    // if (ID != null) {
    // if (ID.equalsIgnoreCase("jd")) {
    // return Arrays.copyOfRange(payloadEncryptionToken, 16, 32);
    // } else if (ID.equalsIgnoreCase("server")) { return Arrays.copyOfRange(serverSecret, 16, 32); }
    // }
    // return null;
    // }
    
    @Override
    protected HttpRequest buildRequest() throws IOException {
        HttpRequest ret = super.buildRequest();
        /* we do not allow gzip output */
        accept_encoding = ret.getRequestHeaders().get("Accept-Encoding");
        ret.getRequestHeaders().remove(HTTPConstants.HEADER_REQUEST_ACCEPT_ENCODING);
        return ret;
    }
    
    @Override
    public void closeConnection() {
        try {
            getOutputStream(true).close();
        } catch (final Throwable nothing) {
            nothing.printStackTrace();
        }
        try {
            this.clientSocket.close();
        } catch (final Throwable nothing) {
        }
    }
    
    @Override
    protected String preProcessRequestLine(String requestLine) throws IOException {
        RequestLineParser parser = RequestLineParser.parse(requestLine.getBytes("UTF-8"));
        if (parser == null || parser.getSessionToken() == null) throw new IOException("Invalid my.jdownloader.org request: " + requestLine);
        requestConnectToken = parser.getSessionToken();
        try {
            SessionInfo session = api.getSessionInfo();
            if (StringUtils.equals(parser.getSessionToken(), session.getSessionToken())) {
                // the request origin is the My JDownloader Server
                payloadEncryptionToken = session.getServerEncryptionToken();
            } else {
                // The request origin is a remote client
                payloadEncryptionToken = api.getDeviceEncryptionTokenBySession(parser.getSessionToken());
                
            }
        } catch (final MyJDownloaderException e) {
            throw new IOException(e);
        }
        requestLine = requestLine.replaceFirst(" /t_[a-zA-z0-9]{40}_.+?/", " /");
        return requestLine;
    };
    
    @Override
    public synchronized OutputStream getOutputStream(boolean sendHeaders) throws IOException {
        if (this.os != null) return this.os;
        HTTPHeader contentType = response.getResponseHeaders().get(HTTPConstants.HEADER_REQUEST_CONTENT_TYPE);
        if (contentType != null && "application/json".equalsIgnoreCase(contentType.getValue())) {
            /* check for json response */
            try {
                boolean deChunk = false;
                HTTPHeader transferEncoding = response.getResponseHeaders().get(HTTPConstants.HEADER_RESPONSE_TRANSFER_ENCODING);
                if (transferEncoding != null) {
                    if (HTTPConstants.HEADER_RESPONSE_TRANSFER_ENCODING_CHUNKED.equalsIgnoreCase(transferEncoding.getValue())) {
                        deChunk = true;
                    } else {
                        throw new IOException("Unsupported TransferEncoding " + transferEncoding);
                    }
                }
                final boolean useDeChunkingOutputStream = deChunk;
                final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                final IvParameterSpec ivSpec = new IvParameterSpec(Arrays.copyOfRange(payloadEncryptionToken, 0, 16));
                final SecretKeySpec skeySpec = new SecretKeySpec(Arrays.copyOfRange(payloadEncryptionToken, 16, 32), "AES");
                cipher.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
                /* remove content-length because we use chunked+base64+aes */
                response.getResponseHeaders().remove(HTTPConstants.HEADER_REQUEST_CONTENT_LENGTH);
                // response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_REQUEST_CONTENT_TYPE,
                // "application/aesjson-jd; charset=utf-8"));
                /* set chunked transfer header */
                response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_RESPONSE_TRANSFER_ENCODING, HTTPConstants.HEADER_RESPONSE_TRANSFER_ENCODING_CHUNKED));
                if (accept_encoding != null && accept_encoding.contains("gzip_aes")) {
                    /* chunked->gzip->aes */
                    response.getResponseHeaders().add(new HTTPHeader(HTTPConstants.HEADER_RESPONSE_CONTENT_ENCODING, "gzip_aes"));
                    this.sendResponseHeaders();
                    if (useDeChunkingOutputStream) {
                        this.os = new DeChunkingOutputStream(new GZIPOutputStream(new CipherOutputStream(new ChunkedOutputStream(clientSocket.getOutputStream(), 16384), cipher)));
                    } else {
                        this.os = new GZIPOutputStream(new CipherOutputStream(new ChunkedOutputStream(clientSocket.getOutputStream(), 16384), cipher));
                    }
                } else {
                    this.sendResponseHeaders();
                    this.os = new OutputStream() {
                        private ChunkedOutputStream chunkedOS = new ChunkedOutputStream(new BufferedOutputStream(clientSocket.getOutputStream(), 16384));
                        Base64OutputStream          b64os     = new Base64OutputStream(chunkedOS) {
                                                                  // public void close() throws IOException {
                                                                  // };
                                                                  
                                                              };
                        OutputStream                outos     = new CipherOutputStream(b64os, cipher);
                        
                        {
                            if (useDeChunkingOutputStream) {
                                outos = new DeChunkingOutputStream(outos);
                            }
                        }
                        
                        @Override
                        public void close() throws IOException {
                            outos.close();
                            b64os.flush();
                            chunkedOS.close();
                        }
                        
                        @Override
                        public void flush() throws IOException {
                        }
                        
                        @Override
                        public void write(int b) throws IOException {
                            outos.write(b);
                        }
                        
                        @Override
                        public void write(byte[] b, int off, int len) throws IOException {
                            outos.write(b, off, len);
                        };
                        
                    };
                }
            } catch (final Throwable e) {
                throw new IOException(e);
            }
        } else {
            if (sendHeaders) {
                this.sendResponseHeaders();
            }
            this.os = super.os;
        }
        return this.os;
    }
}
