import java.io.*;

public class GameInfo implements Serializable
{
    private static final long serialVersionUID = 1L;
    String msg, name;
    int choice, type;

    public GameInfo(int t)
    {
        msg = "";
        name = "";
        choice = -1;
        type = t;
    }
}