package at.aau.group1.leiterspiel;

import android.graphics.Point;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Igor on 18.04.2016.
 */
public class GameBoard {

    private int numberOfFields;
    private ArrayList<Ladder> ladders = new ArrayList<Ladder>();
    private ArrayList<Piece> pieces = new ArrayList<Piece>();

    // for graphics later on
    private GameField[] fields;

    public GameBoard() {

    }

    public GameBoard(int numberOfFields, ArrayList<Ladder> ladders, ArrayList<Piece> pieces) {
        this.numberOfFields = numberOfFields;
        this.ladders = ladders;
        this.pieces = pieces;
        this.fields = new GameField[numberOfFields];
        Arrays.fill(fields, new GameField());
    }

    public void setNumberOfFields(int number) {
        this.numberOfFields = number;
        this.fields = new GameField[this.numberOfFields];
        Arrays.fill(fields, new GameField());
    }

    public GameField[] getFields() {
        return fields;
    }

    public ArrayList<Ladder> getLadders() {
        return ladders;
    }

    public ArrayList<Piece> getPieces() {
        return pieces;
    }

    public int getNumberOfFields() {
        return numberOfFields;
    }

    public void addLadder(Ladder ladder) {
        this.ladders.add(ladder);
    }

    public void addPiece(Piece piece) { this.pieces.add(piece); }

    // moves one of the pieces(the one corresponding to the playerID) by the given amount of fields,
    // considering all ladders.
    // throws IllegalArgumentException if an invalid playerID was given
    // returns true if this move ends the game, otherwise false
    public boolean movePiece(int playerID, int fields) {
        int previousField = 0; // for debug messages only
        boolean gameEnded = false;

        Piece currentPiece = null;
        for (Piece p: pieces) {
            if (p.getPlayerID() == playerID) {
                currentPiece = p;
                previousField = p.getField();
                break;
            }
        }
        if(currentPiece == null) throw new IllegalArgumentException("invalid playerID");
        int currentField = currentPiece.getField();

        // checks if the goal will be reached
        if (currentField + fields == numberOfFields-1) { // TODO end the game properly
//            currentPiece.setField(numberOfFields-1);
            currentField += fields;
            gameEnded = true;
            Log.d("Tag", "Game ended. Winner is player "+playerID);
//            return true;
        } else if (currentField + fields >= numberOfFields) { // TODO specify rules
            // do nothing in case the goal would be overshot
            return false;
        } else {
            currentField += fields;
            // check ladders
            for (Ladder ladder: ladders) {
                if (ladder.checkFields(currentField)) {
                    currentField = ladder.checkActivation(currentField);

                    if (currentField != previousField) Log.d("Tag", "Player "+playerID+" used a ladder");
                    break;
                }
            }
        }
        // move piece to currentField
        currentPiece.setField(currentField);

        // remove highlighting
        for (int f=0; f<numberOfFields; f++) {
            this.fields[f].setHighlighted(false);
        }

        Log.d("Tag", "Player " + playerID + " moved from field " + previousField + " to " + currentField);
        return gameEnded;
    }

    public Piece getPieceOfPlayer(int playerID) {
        for (Piece piece:pieces) {
            if (piece.getPlayerID() == playerID) return piece;
        }
        return null;
    }

    public ArrayList<Piece> getPiecesOnField(int field) {
        ArrayList<Piece> pieces = new ArrayList<Piece>();
        for (Piece piece: pieces) {
            if (field == piece.getField()) pieces.add(piece);
        }
        return pieces;
    }

    public Ladder getLadderOnField(int field) {
        for (Ladder ladder: ladders) {
            if (field == ladder.getStartField() || field == ladder.getEndField()) return ladder;
        }
        return null;
    }

    // for locating the field nearest to a touch input event
    public int getFieldAtPosition(Point point) {
        int nearestField = 0;
        int minDistance = Integer.MAX_VALUE;
        for (int n=0; n<fields.length; n++) {
            GameField field = fields[n];
            int dx = Math.abs(point.x - field.getPos().x);
            int dy = Math.abs(point.y - field.getPos().y);
            int d = (int) Math.hypot(dx, dy);

            if (d < minDistance) {
                minDistance = d;
                nearestField = n;
            }
        }
        return nearestField;
    }

}
