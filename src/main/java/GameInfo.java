import java.io.*;

public class GameInfo implements Serializable
{
    private static final long serialVersionUID = 1L;
    String msg, name, pass;
    int choice, type;

    //Startgame sent to client means this message should throw the client to the lobby
    //Leftgame indicates someone left the game, name of player stored in name
    //Challenge sent to client means we should move from lobby to game
    //InGame sent to a client indicates they tried to challenge someone already in-game, name stored in name field
    //SelfChallenge sent to a client indicates they tried to challenge themselves
    //Serverdrop indicates that the client lost connection to the server (client will send to themselves)
    //Finished sent to a client indicates that we should start the pauseTransition that will move us back to the lobby
    //Finished sent to a server indicates that we need the clientlist from the server
    //-1 = message, 0 = startGame, 1 = leftGame, 2 = challenge, 3 = inGame, 4 = selfChallenge, 5 = serverDrop, 6 = finished, 7 = handshake, 8 = choice, 9 = newPlayer


    public GameInfo(int t)
    {
        msg = "";
        name = "";
        pass = "";
        choice = -1;
        type = t;
    }
}