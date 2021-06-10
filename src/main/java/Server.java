import javafx.application.Platform;

import javax.swing.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.sql.*;

public class Server
{
    TheServer server;
    Consumer<Serializable> callback;
    ArrayList<ClientThread> clients;

    //Database connection
    Connection con;

    Server(Consumer<Serializable> call, int port)
    {
        callback = call;
        server = new TheServer(port);
        clients = new ArrayList<ClientThread>();

        try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            con = DriverManager.getConnection("jdbc:oracle:thin:@108.92.151.97:2204:orcl", "Paul", "joemamma");
            /*Sample sql code
            con.beginRequest();
            Statement stmt = con.createStatement();

            ResultSet rs = stmt.executeQuery("select * from Users");
            while (rs.next()) {
                System.out.println(rs.getString(1) + ": " + rs.getString(2));
            }
            con.endRequest();*/
        } catch (Exception ex)
        {
            System.out.println(ex);
        }
    }

    public void start()
    {
        server.start();
    }

    //Returns: 0 = tie, 1 = 1st client, 2 = 2nd client
    //Expects: 0=rock, 1=paper, 2= scissors, 3=lizard, 4=spock
    public int determineWinner(int c1, int c2)
    {
        if (c1 == 0)
        {
            if (c2 == 1 || c2 == 4)
            {
                return 2;
            }
            else if (c2 == 2 || c2 == 3)
            {
                return 1;
            }
        }
        else if (c1 == 1)
        {
            if (c2 == 0 || c2 == 4)
            {
                return 1;
            }
            else if (c2 == 2 || c2 == 3)
            {
                return 2;
            }
        }
        else if (c1 == 2)
        {
            if (c2 == 1 || c2 == 3)
            {
                return 1;
            }
            else if (c2 == 0 || c2 == 4)
            {
                return 2;
            }
        }
        else if (c1 == 3)
        {
            if (c2 == 1 || c2 == 4)
            {
                return 1;
            }
            else if (c2 == 2 || c2 == 0)
            {
                return 2;
            }
        }
        else if (c1 == 4)
        {
            if (c2 == 0 || c2 == 2)
            {
                return 1;
            }
            else if (c2 == 1 || c2 == 3)
            {
                return 2;
            }
        }
        return 0;
    }

    //Shutdown all threads
    public void shutdown()
    {
        for (int i = 0; i < clients.size(); i++) {
            try
            {
                String time = new Timestamp(System.currentTimeMillis()).toString();
                con.beginRequest();
                Statement st = con.createStatement();
                st.executeQuery("UPDATE Logs SET LOGOUT = timestamp '" + time + "' WHERE LOGOUT is null AND USERNAME = '" + clients.get(i).name + "'");
                con.endRequest();
            }catch (Exception ex)
            {
                System.out.println(ex);
            }
            clients.get(i).kill();
            i--;
        }
        server.kill();
    }

    //Find the client with the matching name
    private synchronized ClientThread getClientWithName(String s)
    {
        for (ClientThread c : clients)
        {
            if (c.hasName && c.name.equals(s))
            {
                return c;
            }
        }
        return null;
    }

    public class TheServer extends Thread
    {
        ServerSocket mySocket;
        private boolean kill = false;
        private int port;

        public TheServer(int p)
        {
            port = p;
            setDaemon(true);
        }

        public void run()
        {
            try
            {
                mySocket = new ServerSocket(port);
                //System.out.println("Server is waiting for a client!");
                callback.accept("Waiting for clients...");

                while(!kill)
                {
                    Socket s = mySocket.accept();
                    ClientThread p = new ClientThread(s);
                    callback.accept("New player connecting...");
                    clients.add(p);
                    p.start();
                }
            }//end of try
            catch(Exception e)
            {
                callback.accept("Server socket did not launch");
            }
        }

        //Kill the thread
        private void kill()
        {
            kill = true;
            try
            {
                mySocket.close();
            }catch(Exception ex)
            {

            }
        }

    }

    public class ClientThread extends Thread
    {
        Socket connection;
        ObjectInputStream in;
        ObjectOutputStream out;
        String opponent;  //Name of opponent if there is one
        private boolean kill = false, hasName = false;
        int choice = -1;
        String name = "", s = "";
        public boolean inGame = false;   //will be true if the player is currently in a game

        ClientThread(Socket s)
        {
            this.connection = s;
            setDaemon(true);
        }

        private String bytestoHex(byte[] hash)
        {
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if(hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        }

        public void run()
        {
            try
            {
                in = new ObjectInputStream(connection.getInputStream());
                out = new ObjectOutputStream(connection.getOutputStream());
                connection.setTcpNoDelay(true);
            }
            catch(Exception e)
            {
                System.out.println("Streams not open");
            }
            //Setting the name
            try
            {
                GameInfo gg = new GameInfo(7);
                gg.msg = "Please choose a name...";
                messagePlayer(gg);
                boolean n = false;
                String n2 = "";
                String p = "";
                do
                {
                    n = false;
                    GameInfo g = (GameInfo)in.readObject();
                    n2 = g.msg;
                    p = g.pass;
                    ClientThread c = getClientWithName(n2);
                    if (c != null)
                    {
                        //Somebody already exists with this name
                        GameInfo g2 = new GameInfo(-1);
                        g2.msg = "Client with that name already logged in, choose another name.";
                        messagePlayer(g2);
                        n = true;
                    }
                    else
                    {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] encodedhash = digest.digest(p.getBytes(StandardCharsets.UTF_8));
                        String hashedPW = bytestoHex(encodedhash);
                        con.beginRequest();
                        Statement stmt = con.createStatement();

                        //System.out.println("select PASSWORD from Users where USERNAME = '" + n2 + "'");
                        ResultSet rs = stmt.executeQuery("select PASSWORD from Users where USERNAME = '" + n2 + "'");
                        boolean validUsername = false;
                        while(rs.next()) {
                            validUsername = true;
                            String actualHash = rs.getString(1);
                            if (!actualHash.equals(hashedPW))
                            {
                                n = true;
                                GameInfo g2 = new GameInfo(-1);
                                g2.msg = "Wrong password!";
                                messagePlayer(g2);
                            }
                            else
                            {
                                GameInfo g2 = new GameInfo(-1);
                                g2.msg = "Connected as user " + n2 + "!";
                                messagePlayer(g2);
                                String finalN = n2;
                                Platform.runLater(()->{
                                    try {
                                        String time = new Timestamp(System.currentTimeMillis()).toString();
                                        con.beginRequest();
                                        Statement st = con.createStatement(), delSt = con.createStatement();
                                        delSt.executeQuery("DELETE from Logs where LOGOUT is null");
                                        String query = "INSERT into Logs values('" + finalN + "', timestamp '" + time + "', null)";
                                        ResultSet res = st.executeQuery(query);
                                        res.close();
                                        delSt.close();
                                        st.close();
                                        con.endRequest();
                                    }catch (Exception ex)
                                    {
                                        System.out.println(ex);
                                    }
                                });
                            }
                        }
                        if (!validUsername)
                        {
                            GameInfo g2 = new GameInfo(-1);
                            g2.msg = "No such user exists.";
                            messagePlayer(g2);
                            n = true;
                        }
                    }
                }while(n);
                con.endRequest();
                hasName = true;
                name = n2;
                callback.accept(name + " has joined the lobby");
                //Message player list to this client
                messagePlayerList(this, false);
                //Message this player to all other clients
                synchronized (clients)
                {
                    for (ClientThread c : clients)
                    {
                        if (!c.hasName)
                            continue;
                        if (!c.inGame)
                        {
                            GameInfo g3 = new GameInfo(-1);
                            g3.msg = name;
                            c.messagePlayer(g3);
                        }

                    }
                }
            }
            catch (Exception ex)
            {

            }
            //Main data loop
            while (!kill)
            {
                try
                {

                    GameInfo g = (GameInfo)in.readObject();
                    //we need to sync this or the list might change while we are looking for an opp
                    synchronized(clients)
                    {
                        if (inGame)
                        {
                            //Player is requesting playerlist to return to lobby
                            if (g.type == 6)
                            {
                                //Reset server-side data
                                inGame = false;
                                choice = -1;
                                s = "";
                                opponent = "";
                                //Send playerlist to client
                                messagePlayerList(this, true);
                            }
                            else
                            {
                                //We got the client's choice
                                choice = g.choice;
                                s = g.msg;
                                //Did opponent also make move?  If so, calculate winner and send results
                                ClientThread c = getClientWithName(opponent);
                                if (c.choice != -1)
                                {
                                    //Send player moves to each player
                                    GameInfo gg = new GameInfo(-1);
                                    gg.msg = "You chose: " + s;
                                    GameInfo g2 = new GameInfo(-1);
                                    g2.msg = "Opponent chose: " + c.s;
                                    messagePlayer(gg);
                                    messagePlayer(g2);
                                    gg.msg = "You chose: " + c.s;
                                    g2.msg = "Opponent chose: " + s;
                                    c.messagePlayer(gg);
                                    c.messagePlayer(g2);

                                    //Determine winner and notify players
                                    int winner = determineWinner(choice, c.choice);
                                    GameInfo gg2 = new GameInfo(6);
                                    gg2.msg = "You win!  Returning to lobby in 5 seconds...";
                                    GameInfo gg3 = new GameInfo(6);
                                    gg3.msg = "You lose!  Returning to lobby in 5 seconds...";
                                    String winnerName = "", loserName = "";
                                    if (winner == 0){
                                        gg2.msg = "Tie game!  Returning to lobby in 5 seconds...";
                                        messagePlayer(gg2);
                                        c.messagePlayer(gg2);
                                    }else {
                                        //This player won
                                        if (winner == 1) {
                                            messagePlayer(gg2);
                                            c.messagePlayer(gg3);
                                            winnerName = name;
                                            loserName = c.name;
                                        }
                                        //This player lost
                                        else if (winner == 2) {
                                            messagePlayer(gg3);
                                            c.messagePlayer(gg2);
                                            loserName = name;
                                            winnerName = c.name;
                                        }
                                        String finalWinnerName = winnerName;
                                        String finalLoserName = loserName;
                                        Platform.runLater(()->{
                                            try {
                                                con.beginRequest();
                                                Statement stmt = con.createStatement(), updateSt = con.createStatement();

                                                //System.out.println("select PASSWORD from Users where USERNAME = '" + n2 + "'");
                                                ResultSet rs = stmt.executeQuery("select COUNT from Wins where WINNER = '" + finalWinnerName + "' AND LOSER ='" + finalLoserName + "'");
                                                boolean hasEntry = false;
                                                while (rs.next()) {
                                                    hasEntry = true;
                                                    int count = rs.getInt(1) + 1;
                                                    updateSt.executeQuery("UPDATE Wins SET COUNT = " + count + " WHERE WINNER ='" + finalWinnerName + "' AND LOSER ='" + finalLoserName + "'");
                                                }
                                                //No entry, create it
                                                if (!hasEntry) {
                                                    updateSt.executeQuery("INSERT INTO Wins values('" + finalWinnerName + "', '" + finalLoserName + "', 1)");
                                                }
                                                rs.close();
                                                stmt.close();
                                                updateSt.close();
                                                con.endRequest();
                                            }catch (Exception ex)
                                            {
                                                System.out.println(ex);
                                            }
                                        });
                                    }
                                }
                            }
                        }
                        else
                        {
                            String op = g.msg;  //save the name of the opponent
                            //search for the opponent and challenge them to a game. Also set their inGame == true
                            ClientThread c = getClientWithName(op);
                            if (c != null)
                            {
                                //Player didn't challenge themselves
                                if (!c.name.equals(name))
                                {
                                    if(!c.inGame)
                                    {
                                        GameInfo g2 = new GameInfo(2);
                                        //send message to the challenging player
                                        g2.msg = "Joining game with " + op + "...";
                                        g2.name = op;
                                        this.opponent = op;
                                        this.inGame = true;
                                        this.messagePlayer(g2);

                                        GameInfo g3 = new GameInfo(2);
                                        //send message to the challenged player
                                        g3.msg = "Joining game with " + this.name + "...";
                                        g3.name = this.name;
                                        c.opponent = this.name;
                                        c.messagePlayer(g3);
                                        c.inGame = true;
                                    }
                                    else
                                    {
                                        GameInfo g2 = new GameInfo(3);
                                        //Player tried to challenge someone already ingame
                                        g2.name = op;
                                        messagePlayer(g2);
                                    }
                                }
                                //Player challenged themselves
                                else
                                {
                                    GameInfo g2 = new GameInfo(4);
                                    messagePlayer(g2);
                                }
                            }
                        }
                    }

                }
                //Client has lost connection
                catch (Exception ex)
                {
                    synchronized(clients){

                        clients.remove(this);
                        kill = true;
                        if (hasName)
                        {
                            try
                            {
                                String time = new Timestamp(System.currentTimeMillis()).toString();
                                con.beginRequest();
                                Statement st = con.createStatement();
                                st.executeQuery("UPDATE Logs SET LOGOUT = timestamp '" + time + "' WHERE LOGOUT is null AND USERNAME = '" + name + "'");
                                con.endRequest();
                            }catch (Exception ex2)
                            {
                                System.out.println(ex2);
                            }
                            callback.accept(name + " left the game!");
                            GameInfo g = new GameInfo(1);
                            g.name = name;
                            messagePlayers(g);
                            //Boot opponent to lobby if they're ingame
                            if (inGame)
                            {
                                ClientThread op = getClientWithName(opponent);
                                op.choice = -1;
                                op.s = "";
                                op.opponent = "";
                                op.inGame = false;

                                messagePlayerList(op, true);
                            }
                        }
                        else
                            callback.accept("Client left before selecting a name.");
                    }
                }

            }

        }

        //Message the client with all the players in the server
        private synchronized void messagePlayerList(ClientThread op, boolean includeSelf)
        {
            GameInfo gg = new GameInfo(0);
            gg.msg = "Players Connected(Click a name to challenge): ";
            op.messagePlayer(gg);
            for (ClientThread c : clients)
            {
                if (c.hasName && ((includeSelf) || (!includeSelf && c != this)))
                {
                    GameInfo g2 = new GameInfo(-1);
                    g2.msg = c.name;
                    op.messagePlayer(g2);
                }
            }
        }

        //Message this player
        //Yo, this should be synchronized right? cause if multiple players leave at the same time it could mess with stuff
        public synchronized void messagePlayer(GameInfo g)
        {
            try
            {
                out.writeObject(g);
            }
            catch (Exception ex)
            {

            }
        }

        //Message all players on the server
        public synchronized void messagePlayers(GameInfo g)
        {
            try
            {
                for (ClientThread c : clients)
                {
                    //Should only message a player if they are not in game
                    if(!c.inGame)
                        c.out.writeObject(g);
                }

            }
            catch (Exception ex)
            {

            }
        }

        public void kill()
        {
            kill = true;
            try
            {
                connection.close();
                con.close();
            }catch (Exception ex)
            {

            }
        }
    }
}
