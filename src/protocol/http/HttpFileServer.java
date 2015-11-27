/**
 * 
 */
package protocol.http;

import java.io.File;

import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;

import static  io.netty.handler.codec.http.HttpHeaderNames.*;
import static  io.netty.handler.codec.http.HttpVersion.*;
import static  io.netty.handler.codec.http.HttpResponseStatus.*;
import static  io.netty.handler.codec.http.HttpMethod.*;
import static  io.netty.handler.codec.http.HttpVersion.*;
/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-20
 * 
 */
public class HttpFileServer {

    private static final String DEFAULT_URL = "/src";
    
    public static void main(String[] args) {
        int port = 8080;
        String url = DEFAULT_URL;
        new HttpFileServer().run(url, port);
    }
    
    public void run(final String url,final int port){
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                           .channel(NioServerSocketChannel.class)
                           .childHandler(new ChannelInitializer<SocketChannel>(){
                                @Override
                                protected void initChannel(SocketChannel ch) throws Exception {
                                    ch.pipeline().addLast("http-decoder",new HttpRequestDecoder());
                                    ch.pipeline().addLast("http-aggregator",new HttpObjectAggregator(65535));
                                    ch.pipeline().addLast("http-encoder", new HttpResponseEncoder());
                                    ch.pipeline().addLast("http-chunked", new ChunkedWriteHandler());
                                    ch.pipeline().addLast("firstServerHandler", new HttpFileServerHandler(url));
                                }
                           });
            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("HTTP file server start,port is " + port + " ; url is " + url);
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    
    static class HttpFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest>{
        
        public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";  
        public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";  
        public static final int HTTP_CACHE_SECONDS = 60; 

       private final String url;
       
       public HttpFileServerHandler(String url){
           this.url = url;
       }
       
        @Override
        protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest request)
                throws Exception {
            if(!request.decoderResult().isSuccess()){
                sendError(ctx,BAD_REQUEST);
                return;
            }
            if(request.method() != GET){
                sendError(ctx,METHOD_NOT_ALLOWED);
                return;
            }
            final String uri = request.uri();
            String path = sanitizeUri(uri);
            if(path == null){
                sendError(ctx,FORBIDDEN);
                return;
            }
            File file = new File(path);
            if(file.isHidden() || !file.exists()){
                sendError(ctx,NOT_FOUND);
                return;
            }
            if(file.isDirectory()){
                if(uri.endsWith("/")){
                    sendList(ctx,file);
                }else{
                    sendRedirect(ctx,uri + "/");
                }
                return;
            }
            if(!file.isFile()){
                sendError(ctx,FORBIDDEN);
            }
            
            String ifModifiedSince = request.headers().getAndConvert(IF_MODIFIED_SINCE);
            if(ifModifiedSince != null && ! ifModifiedSince.isEmpty()){
                SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT,Locale.US);
                Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);
                long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
                long fileLastModifiedSeconds = file.lastModified() / 1000;
                if(ifModifiedSinceDateSeconds == fileLastModifiedSeconds){
                    sendNotModified(ctx);
                    return;
                }
            }
            RandomAccessFile randomAccessFile = null;
            try{
                randomAccessFile = new RandomAccessFile(file,"r");
            }catch(FileNotFoundException e){
                sendError(ctx,NOT_FOUND);
                return;
            }
            long fileLength = randomAccessFile.length();
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1,OK);
            this.setContentLength(response, fileLength);
            this.setContentTypeHeader(response, file);
            if(isKeepAlive(request)){
                response.headers().set(CONNECTION,HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.write(response);
            ChannelFuture sendFileFuture ;
            sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile,0,fileLength,8092),ctx.newProgressivePromise());
            sendFileFuture.addListener(new ChannelProgressiveFutureListener(){

                @Override
                public void operationProgressed(ChannelProgressiveFuture future, long progress,
                        long total) throws Exception {
                    if(total < 0){
                        System.out.println("Transfer progress: " + total);
                    }else{
                        System.out.println("Transfer progress: " + progress + "/" + total);
                    }
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future) throws Exception {
                    System.out.println("Transfer completed. ");
                }
                
            });
            
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if(!isKeepAlive(request)){
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            if(ctx.channel().isActive()){
                sendError(ctx,INTERNAL_SERVER_ERROR);
            }
        }
        
        private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*"); 
        
        private String sanitizeUri(String uri){
            try {
                uri = URLDecoder.decode(uri,"UTF-8");
            } catch (UnsupportedEncodingException e) {
                try {
                    uri = URLDecoder.decode(uri,"ISO-8859-1");
                } catch (UnsupportedEncodingException e1) {
                    throw new Error();
                }
            }
            if(!uri.startsWith(url)){
                return null;
            }
            if(!uri.startsWith("/")){
                return null;
            }
            uri = uri.replace('/', File.separatorChar);
            if (uri.contains(File.separator + '.') ||  
                    uri.contains('.' + File.separator) ||  
                    uri.startsWith(".") || uri.endsWith(".") ||  
                    INSECURE_URI.matcher(uri).matches()) {  
               return null;  
            } 
            return System.getProperty("user.dir") + File.separator + uri;
        }
        
        private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[A-Za-z0-9][-A-Za-z0-9\\.]*");
        
        private  void sendList(ChannelHandlerContext ctx,File dir){
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,OK);
            response.headers().set(CONTENT_TYPE,"text/html;charset=UTF-8");
            StringBuilder buf = new StringBuilder();
            String dirPath = dir.getPath();
            buf.append("<!DOCTYPE html>\r\n");  
            buf.append("<html><head><title>");  
            buf.append("Listing of: ");  
            buf.append(dirPath);  
            buf.append("</title></head><body>\r\n");  
            buf.append("<h3>Listing of: ");  
            buf.append(dirPath);  
            buf.append("</h3>\r\n");  
            buf.append("<ul>");  
            buf.append("<li><a href=\"../\">..</a></li>\r\n"); 
            for(File f : dir.listFiles()){
                if(f.isHidden() || !f.canRead()){
                    continue;
                }
                String name = f.getName();
                if(!ALLOWED_FILE_NAME.matcher(name).matches()){
                    continue;
                }
                buf.append("<li><a href=\"");  
                buf.append(name);  
                buf.append("\">");  
                buf.append(name);  
                buf.append("</a></li>\r\n");
            }
            buf.append("</ul></body></html>\r\n");
            ByteBuf buffer = Unpooled.copiedBuffer(buf,CharsetUtil.UTF_8);
            response.content().writeBytes(buffer);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        
        private void sendRedirect(ChannelHandlerContext ctx,String newUri){
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,FOUND);
            response.headers().set(LOCATION,newUri);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        
        private void sendError(ChannelHandlerContext ctx,HttpResponseStatus status){
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,status,
                    Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n",CharsetUtil.UTF_8));
            response.headers().set(CONTENT_TYPE,"text/plain;charset=UTF-8");
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        
        private void setContentTypeHeader(HttpResponse response,File file){
            MimetypesFileTypeMap mimetypesMap = new MimetypesFileTypeMap();
            response.headers().set(CONTENT_TYPE,mimetypesMap.getContentType(file.getPath()));
        }
        
        private void setContentLength(HttpResponse response,long length){
            response.headers().set(CONTENT_LENGTH,String.valueOf(length));
        }
        
        private boolean isKeepAlive(FullHttpRequest req){
            return false;
        }
        
        private void sendNotModified(ChannelHandlerContext ctx){
            FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,NOT_MODIFIED);
            setDateHeader(response);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
        
        private void setDateHeader(FullHttpResponse response){
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT,Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
            Calendar time = new GregorianCalendar();
            response.headers().set(DATE,dateFormatter.format(time.getTime()));
        }
        
        private void setDateAndCacheHeader(HttpResponse response,File fileToCache){
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT,Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
            Calendar time = new GregorianCalendar();
            response.headers().set(DATE,dateFormatter.format(time.getTime()));
            
            time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
            response.headers().set(EXPIRES,dateFormatter.format(time.getTime()));
            response.headers().set(CACHE_CONTROL,"private;max-age=" + HTTP_CACHE_SECONDS);
            response.headers().set(LAST_MODIFIED,dateFormatter.format(new Date(fileToCache.lastModified())));
            
        }
    }
}
