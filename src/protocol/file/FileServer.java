/**
 * 
 */
package protocol.file;

import java.io.File;
import java.io.RandomAccessFile;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

/**
 * 类/接口注释
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-24
 * 
 */
public class FileServer {

   
    public static void main(String[] args) {
        int port = 8080;
        new FileServer().run(port);
    }
    
    public void run(final int port){
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try{
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                           .channel(NioServerSocketChannel.class)
                           .option(ChannelOption.SO_BACKLOG, 100)
                           .childHandler(new ChannelInitializer<SocketChannel>(){
                                @Override
                                protected void initChannel(SocketChannel ch) throws Exception {
                                    ch.pipeline().addLast(new StringEncoder(CharsetUtil.UTF_8),
                                            new LineBasedFrameDecoder(1024),
                                            new StringDecoder(CharsetUtil.UTF_8),
                                            new FileServerHandler());
                                }
                           });
            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("File server start,port is " + port );
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    static class FileServerHandler extends SimpleChannelInboundHandler<String>{
      
        private static final String CR = System.getProperty("line.separator");
        
        @Override
        protected void messageReceived(ChannelHandlerContext ctx, String msg) throws Exception {
            File file = new File(msg);
            if(file.exists()){
                if(!file.isFile()){
                    ctx.writeAndFlush("Not a file" + file + CR);
                    return;
                }
                ctx.write(file + " " + file.length() + CR);
                RandomAccessFile raf = new RandomAccessFile(msg,"r");
                FileRegion fileRegion = new DefaultFileRegion(raf.getChannel(),0,raf.length());
                ctx.write(fileRegion);
                ctx.writeAndFlush(CR);
                raf.close();
            }else{
                ctx.writeAndFlush("File not found " + file + CR);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }
        
        
    }
}
