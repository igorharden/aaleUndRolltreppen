package at.aau.group1.leiterspiel.Game;

/**
 * Created by Igor on 21.04.2016.
 */
public interface IPlayerObserver {

    public void move(int playerID, int diceRoll, boolean localMove);

    public int rollDice(int playerID);

//    public int getDiceResult();

}
