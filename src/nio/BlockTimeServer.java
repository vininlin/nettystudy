/**
 * 
 */
package nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-17
 * 
 */
public class BlockTimeServer {

    public static void main(String[] args) {
        int port = 8080;
        ServerSocket server = null;
        try {
            server = new ServerSocket(port);
            System.out.println("TimeServer is start on " + port);
            Socket socket = null;
            while(true){
                socket = server.accept();
                new Thread(new TimeServerHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            if(server != null){
                System.out.println("Server close..");
                try {
                    server.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                server = null;
            }
        }

    }
    
    static class TimeServerHandler implements Runnable{
        
        private Socket socket ;
        
        private TimeServerHandler(Socket socket){
            this.socket = socket;
        }

        @Override
        public void run() {
            BufferedReader in = null;
            PrintWriter out = null;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(),true);
                String currentTime = null;
                String body = null;
                while(true){
                    body = in.readLine();
                    if(body == null)
                        break;
                    System.out.println("The TimeServer recieve order : " + body);
                    currentTime = "QUERY TIME ORDER".equalsIgnoreCase(body) ?
                            new Date(System.currentTimeMillis()).toString() : "BAD ORDER";
                    out.println(currentTime);
                }
            } catch (IOException e) {
                try{
                    if(in != null) 
                        in.close();
                    if(out != null)
                        out.close();
                    if(socket != null)
                        socket.close();
                }catch(IOException e1){
                    e1.printStackTrace();
                }
            }
        }
        
        
    }

}
