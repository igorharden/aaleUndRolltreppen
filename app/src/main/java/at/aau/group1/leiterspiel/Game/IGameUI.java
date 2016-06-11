package at.aau.group1.leiterspiel.Game;

import android.view.View;

/**
 * Created by Igor on 22.04.2016.
 */
public interface IGameUI {

    public int rollDice(View view);

    public void disableUI();
    public void enableUI();

    public void showStatus(String status);
    public void endGame(Player winner);
    public void skipTurn();

}
