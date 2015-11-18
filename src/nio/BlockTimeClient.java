/**
 * 
 */
package nio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * Àà/½Ó¿Ú×¢ÊÍ
 * 
 * @author linwn@ucweb.com
 * @createDate 2015-11-17
 * 
 */
public class BlockTimeClient {

    public static void main(String[] args){
        int port = 8080;
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out = null;
        try{
            socket = new Socket("127.0.0.1",port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(),true);
            out.println("QUERY TIME ORDER");
            System.out.println("send order to server succeed");
            String resp = in.readLine();
            System.out.println("Now is " + resp);
        }catch(Exception e){
            try{
                if(out != null)
                    out.close();
                if(in != null)
                    in.close();
                if(socket != null)
                    socket.close();
            }catch(IOException e1){
                
            }
        }
        
    }
}
