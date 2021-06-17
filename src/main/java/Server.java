import javafx.application.Platform;
import javafx.util.Pair;

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
import java.util.List;
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
        String name = "", s = "", time;
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
                    //Somebody with that name already exists
                    if (c != null && g.type != 9)
                    {
                        GameInfo g2 = new GameInfo(-1);
                        g2.msg = "Client with that name already logged in, choose another name.";
                        messagePlayer(g2);
                        n = true;
                    }
                    //Trying to add a new player
                    else if (g.type == 9)
                    {
                        String finalN = n2;
                        List<String> queries = new ArrayList<String>() {
                            {
                                add("SELECT count(*) from Users where USERNAME = '" + finalN + "'");
                            }
                        };
                        Pair<List<ResultSet>, List<Statement>> result = getMultipleQueryResults(queries);
                        List<ResultSet> resultSets = result.getKey();
                        List<Statement> statements = result.getValue();
                        boolean alreadyExists = false;
                        for (int i = 0; i < resultSets.size(); i++)
                        {
                            while (resultSets.get(i).next())
                            {
                                if (resultSets.get(i).getInt(1) > 0)
                                {
                                    alreadyExists = true;
                                }
                                break;
                            }
                            resultSets.get(i).close();
                            statements.get(i).close();
                        }
                        if (alreadyExists)
                        {
                            n = true;
                            GameInfo g2 = new GameInfo(-1);
                            g2.msg = "Client with that name already exists, pick a new name.";
                            messagePlayer(g2);
                        }
                        else
                        {
                            //Adding a new player to the database
                            if (g.pass.length() > 0)
                            {
                                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                                byte[] encodedhash = digest.digest(p.getBytes(StandardCharsets.UTF_8));
                                String hashedPW = bytestoHex(encodedhash);

                                String finalN1 = n2;
                                List<String> createUserQuery = new ArrayList<>(){
                                    {
                                        add("INSERT into Users values('" + finalN1 + "', '" + hashedPW +  "')");
                                    }
                                };
                                Pair<List<ResultSet>, List<Statement>> createUserPair = getMultipleQueryResults(createUserQuery);
                                List<ResultSet> createUserResultSets = createUserPair.getKey();
                                List<Statement> createUserStatements = createUserPair.getValue();
                                for (int i = 0; i < createUserResultSets.size(); i++)
                                {
                                    createUserResultSets.get(i).close();
                                    createUserStatements.get(i).close();
                                }
                                GameInfo g2 = new GameInfo(-1);
                                g2.msg = "Connected as user " + n2 + "!";
                                messagePlayer(g2);
                                time = new Timestamp(System.currentTimeMillis()).toString();
                            }
                            else
                            {
                                n = true;
                                GameInfo g2 = new GameInfo(-1);
                                g2.msg = "Password must not be empty.";
                                messagePlayer(g2);
                            }
                        }
                    }
                    //Client that isn't logged in and we are trying to login
                    else
                    {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] encodedhash = digest.digest(p.getBytes(StandardCharsets.UTF_8));
                        String hashedPW = bytestoHex(encodedhash);
                        String finalN2 = n2;
                        List<String> passwordQueries = new ArrayList<>(){
                            {
                                add("select PASSWORD from Users where USERNAME = '" + finalN2 + "'");
                            }
                        };
                        Pair<List<ResultSet>, List<Statement>> passwordQuery = getMultipleQueryResults( passwordQueries);
                        List<ResultSet> passwordResultSets = passwordQuery.getKey();
                        List<Statement> passwordStatements = passwordQuery.getValue();
                        boolean validUsername = false;
                        String actualHash = "";
                        for (int i = 0; i < passwordResultSets.size(); i++)
                        {
                            while (passwordResultSets.get(i).next())
                            {
                                validUsername = true;
                                actualHash = passwordResultSets.get(i).getString(1);
                                break;
                            }
                            passwordResultSets.get(i).close();
                            passwordStatements.get(i).close();
                        }
                        if (validUsername)
                        {
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
                                time = new Timestamp(System.currentTimeMillis()).toString();
                            }
                        }else
                        {
                            GameInfo g2 = new GameInfo(-1);
                            g2.msg = "No such user exists.";
                            messagePlayer(g2);
                            n = true;
                        }
                    }
                }while(n);
                hasName = true;
                name = n2;
                //TODO: Update other players about their stats in accordance to this player
                updatePlayers(this);
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
                        if (c != this)
                        {
                            GameInfo g3 = new GameInfo(10);
                            updatePlayers(c);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                System.out.println(ex);
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
                            else if (g.type == 8)
                            {
                                //We got the client's choice
                                choice = g.choice;
                                s = g.msg;
                                //Did opponent also make move?  If so, calculate winner and send results
                                ClientThread c = getClientWithName(opponent);
                                if (c.choice != -1)
                                {
                                    List<String> winUpdateQueries = new ArrayList<String>(){
                                        {
                                            add("SELECT * from Playermoves where USERNAME  = '" + name + "'");
                                            add("SELECT * from Playermoves where USERNAME = '" + opponent + "'");
                                        }
                                    };
                                    String mColName = "", oColName = "";
                                    int myChoiceCount = -1, oChoiceCount = -1;
                                    Pair<List<ResultSet>, List<Statement>> getWinStats = getMultipleQueryResults(winUpdateQueries);
                                    List<ResultSet> getWinResultSets = getWinStats.getKey();
                                    List<Statement> getWinStatements = getWinStats.getValue();
                                    for (int i = 0; i < getWinResultSets.size(); i++)
                                    {
                                        while (getWinResultSets.get(i).next()) {
                                            switch (i) {
                                                case 0:
                                                    mColName = getWinResultSets.get(i).getMetaData().getColumnName(choice + 2);
                                                    myChoiceCount = getWinResultSets.get(i).getInt(choice + 2) + 1;
                                                    break;
                                                case 1:
                                                    oColName = getWinResultSets.get(i).getMetaData().getColumnName(c.choice + 2);
                                                    oChoiceCount = getWinResultSets.get(i).getInt(c.choice + 2) + 1;
                                                    break;
                                            }
                                            break;
                                        }
                                        getWinResultSets.get(i).close();
                                        getWinStatements.get(i).close();
                                    }
                                    String finalMColName = mColName;
                                    int finalMyChoiceCount = myChoiceCount;
                                    String finalOColName = oColName;
                                    int finalOChoiceCount = oChoiceCount;
                                    List<String> updateWinQueries = new ArrayList<String>(){
                                        {
                                            add("UPDATE Playermoves set " + finalMColName + " = " + finalMyChoiceCount + " where USERNAME = '" + name + "'");
                                            add("UPDATE Playermoves set " + finalOColName + " = " + finalOChoiceCount + " where USERNAME = '" + c.name + "'");
                                        }
                                    };
                                    Pair<List<ResultSet>, List<Statement>> updateWinStats = getMultipleQueryResults(updateWinQueries);
                                    List<ResultSet> updateWinResultSets = updateWinStats.getKey();
                                    List<Statement> updateWinStatements = updateWinStats.getValue();
                                    for (int i = 0; i < updateWinResultSets.size(); i++)
                                    {
                                        updateWinResultSets.get(i).close();
                                        updateWinStatements.get(i).close();
                                    }
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
                                        List<String> getWinCountQuery = new ArrayList<String>(){
                                            {
                                                add("UPDATE Wins SET COUNT = (SELECT COUNT FROM Wins WHERE WINNER = '" + finalWinnerName + "' AND LOSER = '" + finalLoserName + "') + 1 WHERE WINNER = '" + finalWinnerName + "' AND LOSER = '" + finalLoserName + "'");
                                            }
                                        };
                                        Pair<List<ResultSet>, List<Statement>> winCountStats = getMultipleQueryResults(getWinCountQuery);
                                        List<ResultSet> winCountResultSets = winCountStats.getKey();
                                        List<Statement> winCountStatements = winCountStats.getValue();
                                        for (int i = 0; i < winCountResultSets.size(); i++)
                                        {
                                            winCountResultSets.get(i).close();
                                            winCountStatements.get(i).close();
                                        }
                                        updatePlayers(this);
                                        updatePlayers(c);
                                    }
                                }
                            }
                        }
                        else
                        {
                            if (g.type == 11)
                            {
                                GameInfo g2 = new GameInfo(11);
                                List<String> leaderboardQuery = new ArrayList<String>(){
                                    {
                                        add("select t.WINNER, avg(t.COUNT/NULLIF((SELECT w.COUNT FROM WINS w WHERE w.LOSER=t.WINNER AND w.WINNER = t.LOSER), 0)) as AvgWinLossRatio From Wins t Where Winner = t.WINNER GROUP BY t.WINNER ORDER BY AvgWinLossRatio desc nulls last FETCH FIRST 10 ROWS ONLY");
                                    }
                                };
                                Pair<List<ResultSet>, List<Statement>> leaderboardStats = getMultipleQueryResults(leaderboardQuery);
                                for (int i = 0; i < leaderboardStats.getKey().size(); i++)
                                {
                                    while (leaderboardStats.getKey().get(i).next())
                                    {
                                        g2.msg += "Player: " + leaderboardStats.getKey().get(i).getString(1) + ", AvgWinLossRatio: " + leaderboardStats.getKey().get(i).getFloat(2) + "\n";
                                    }
                                    leaderboardStats.getKey().get(i).close();
                                    leaderboardStats.getValue().get(i).close();
                                }
                                messagePlayer(g2);
                            }
                            else {
                                String op = g.msg;  //save the name of the opponent
                                //search for the opponent and challenge them to a game. Also set their inGame == true
                                ClientThread c = getClientWithName(op);
                                if (c != null) {
                                    //Player didn't challenge themselves
                                    if (!c.name.equals(name)) {
                                        if (!c.inGame) {
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
                                        } else {
                                            GameInfo g2 = new GameInfo(3);
                                            //Player tried to challenge someone already ingame
                                            g2.name = op;
                                            messagePlayer(g2);
                                        }
                                    }
                                    //Player challenged themselves
                                    else {
                                        GameInfo g2 = new GameInfo(4);
                                        g2.name = name;
                                        List<String> playerStatQueries = new ArrayList<String>() {
                                            {
                                                add("select t.WINNER, avg(t.COUNT/NULLIF((SELECT w.COUNT FROM WINS w WHERE w.LOSER = t.WINNER AND w.WINNER = t.LOSER), 0)) from WINS t WHERE WINNER = '" + name + "' GROUP BY t.WINNER");
                                                add("select SUM(COUNT) FROM WINS WHERE LOSER = '" + name + "'");
                                                add("SELECT LOGIN, LOGOUT FROM Logs WHERE USERNAME = '" + name + "' ORDER BY LOGIN desc FETCH FIRST 3 ROWS ONLY");
                                                add("SELECT * from Playermoves WHERE USERNAME = '" + name + "'");
                                                add("SELECT SUM(COUNT) FROM Wins WHERE WINNER = '" + name + "'");
                                            }
                                        };
                                        Pair<List<ResultSet>, List<Statement>> playerStats = getMultipleQueryResults(playerStatQueries);
                                        List<ResultSet> playerResultSets = playerStats.getKey();
                                        List<Statement> playerStatements = playerStats.getValue();
                                        float myWLRatio = 0;
                                        int losses = 0, wins = 0;
                                        for (int i = 0; i < playerResultSets.size(); i++) {
                                            if (i == 2)
                                                g2.msg += "Player's Last 3 Sessions\n--------------------------\n";
                                            else if (i == 3)
                                                g2.msg += "Player's Move Stats\n----------------------\n";
                                            while (playerResultSets.get(i).next()) {
                                                switch (i) {
                                                    case 0:
                                                        myWLRatio = playerResultSets.get(i).getFloat(2);
                                                        break;
                                                    case 1:
                                                        losses = playerResultSets.get(i).getInt(1);
                                                        break;
                                                    case 2:
                                                        g2.msg += "Login: " + playerResultSets.get(i).getTimestamp(1) + ", Logout: " + playerResultSets.get(i).getTimestamp(2) + "\n\n";
                                                        break;
                                                    case 3:
                                                        g2.msg += "Rock: " + playerResultSets.get(i).getInt(2) + ", Paper: " + playerResultSets.get(i).getInt(3) + ", Scissors: " + playerResultSets.get(i).getInt(4) + ", Lizard: " + playerResultSets.get(i).getInt(5) + ", Spock: " + playerResultSets.get(i).getInt(6) + "\n\n";
                                                        break;
                                                    case 4:
                                                        wins = playerResultSets.get(i).getInt(1);
                                                        break;
                                                }
                                                if (i != 2)
                                                    break;
                                            }
                                            playerResultSets.get(i).close();
                                            playerStatements.get(i).close();
                                        }
                                        g2.msg += "Win/Loss Ratio: " + myWLRatio + "\n\nLosses: " + losses + "\n\nWins: " + wins;
                                        messagePlayer(g2);
                                    }
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
                                String logoutTime = new Timestamp(System.currentTimeMillis()).toString();
                                List<String> updateLogQuery = new ArrayList<String>(){
                                    {
                                        add("INSERT into Logs values('" + name + "', timestamp '" + time + "', timestamp '" + logoutTime + "')");
                                    }
                                };
                                Pair<List<ResultSet>, List<Statement>> updateLogs = getMultipleQueryResults(updateLogQuery);
                                for (int i = 0; i < updateLogs.getKey().size(); i++)
                                {
                                    updateLogs.getKey().get(i).close();
                                    updateLogs.getValue().get(i).close();
                                }
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

        private synchronized void updatePlayers(ClientThread myC) throws Exception
        {
            for (ClientThread iclient : clients)
            {
                if (iclient != myC)
                {
                    GameInfo g = new GameInfo(10);
                    g.msg = myC.name;
                    String tooltip = "";
                    ArrayList<String> playerStatQueries = new ArrayList<String>(){
                        {
                            //Losses against player c
                            add("SELECT COUNT FROM Wins WHERE WINNER = '" + myC.name + "' AND LOSER = '" + iclient.name + "'");
                            //Wins against player c
                            add("SELECT COUNT FROM Wins WHERE WINNER = '" + iclient.name + "' AND LOSER = '" + myC.name + "'");
                            //Win/Loss Ratio
                            add("SELECT t.COUNT/NULLIF((SELECT w.COUNT FROM WINS w WHERE w.LOSER = t.WINNER AND w.WINNER = t.LOSER), 0) FROM Wins t WHERE WINNER = '" + iclient.name + "' AND LOSER = '" + myC.name + "'");
                            //Other players stats
                            add("SELECT * FROM Playermoves WHERE USERNAME = '" + myC.name + "'");
                        }
                    };
                    Pair<List<ResultSet>, List<Statement>> playerOpponentStats = getMultipleQueryResults(playerStatQueries);
                    for (int i = 0; i < playerOpponentStats.getKey().size(); i++)
                    {
                        switch (i)
                        {
                            case 0:
                                g.name += "Losses against\n-------------\n";
                                while (playerOpponentStats.getKey().get(i).next())
                                {
                                    int losses = playerOpponentStats.getKey().get(i).getInt(1);
                                    g.name += losses + "\n\n";
                                    break;
                                }
                                break;
                            case 1:
                                g.name += "Wins against\n-----------\n";
                                while (playerOpponentStats.getKey().get(i).next())
                                {
                                    int wins = playerOpponentStats.getKey().get(i).getInt(1);
                                    g.name += wins + "\n\n";
                                    break;
                                }
                                break;
                            case 2:
                                g.name += "Win/LossRatio\n-------------\n";
                                while (playerOpponentStats.getKey().get(i).next())
                                {
                                    g.name += playerOpponentStats.getKey().get(i).getFloat(1) + "\n\n";
                                    break;
                                }
                                break;
                            case 3:
                                g.name += "Playermove Stats\n----------------\n";
                                while (playerOpponentStats.getKey().get(i).next())
                                {
                                    g.name += "Rock: " + playerOpponentStats.getKey().get(i).getInt(2) + ", Paper: " + playerOpponentStats.getKey().get(i).getInt(3) + ", Scissors: " + playerOpponentStats.getKey().get(i).getInt(4) + ", Lizard: " + playerOpponentStats.getKey().get(i).getInt(5) + ", Spock: " + playerOpponentStats.getKey().get(i).getInt(6) + "\n\n";
                                    break;
                                }
                                break;
                        }
                        playerOpponentStats.getKey().get(i).close();
                        playerOpponentStats.getValue().get(i).close();
                    }
                    iclient.messagePlayer(g);
                }
            }
        }

        //Message the client with all the players in the server
        private synchronized void messagePlayerList(ClientThread op, boolean includeSelf)
        {
            GameInfo gg = new GameInfo(0);
            gg.msg = "Players Connected(Hover over opponent name to view stats, click to challenge, click your name to view your stats, and click here to view leaderboard): ";
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

        private Pair<List<ResultSet>, List<Statement>> getMultipleQueryResults(List<String> queries)
        {
            try
            {
                con.beginRequest();
                List<ResultSet> resultSets = new ArrayList<ResultSet>();
                List<Statement> statements = new ArrayList<Statement>();

                for (String s : queries)
                {
                    Statement st = con.createStatement();
                    resultSets.add(st.executeQuery(s));
                    statements.add(st);
                }
                con.endRequest();

                return new Pair<List<ResultSet>, List<Statement>>(resultSets, statements);
            } catch (Exception ex)
            {
                System.out.println(ex);
            }
            return null;
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
