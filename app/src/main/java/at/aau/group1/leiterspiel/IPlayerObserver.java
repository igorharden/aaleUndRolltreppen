package at.aau.group1.leiterspiel;

/**
 * Created by Igor on 21.04.2016.
 */
public interface IPlayerObserver {

    public void move(int playerID, int diceRoll);

    public int rollDice(int playerID);

}
