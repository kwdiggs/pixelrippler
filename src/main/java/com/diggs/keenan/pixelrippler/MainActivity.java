package com.diggs.keenan.pixelrippler;

import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private MediaPlayer mPlayer;

    // board, theme, and color palette
    private View[][] gameBoard;
    private final int BOARD_WIDTH = 7;
    private final int BOARD_HEIGHT = 12;
    private final int THEME_LIGHT = 1;
    private final int THEME_DARK = 2;
    private final int THEME_GRADIENT = 3;
    private final int THEME_EARTH = 4;
    private int currentTheme = THEME_LIGHT;
    private int[] layer; // the current color palette, which can be envisioned as additive layers
    private int blue = 0xFF3FE1FA;
    private int red = 0xFFFF0000;

    // event variables, used to determine if a specific event has transpired
    private int sameTileCount = 0;
    private int alternatingTilesCount = 0;
    private final int SAME_TILE_THRESH = 10; // threshold, or # times action taken before new event
    private final int ALTERNATING_TILE_THRESH = 15;
    private final int THEME_CHANGE_THRESH = 7;
    private int themeChangeCount = 0;
    private int prevX = -1;
    private int prevY = -1;
    private int prevPrevX = -1;
    private int prevPrevY = -1;

    // achievements
    private boolean[] achievements;
    private final int ACHIEVEMENT_RIPPLER = 0;
    private final int ACHIEVEMENT_PYROTECHNICIAN = 1;
    private final int ACHIEVEMENT_EXPLORER = 2;
    private final int ACHIEVEMENT_CHOSEN_ONE = 3;
    private final int ACHIEVEMENT_DESTROYER_OF_WORLDS = 4;
    private final int ACHIEVEMENT_MASTER_OF_TIME = 5;
    private final int ACHIEVEMENT_GLADIATOR = 6;
    private final int ACHIEVEMENT_VICTOR = 7;

    // matrix animation variables
    final private Handler lineHandler = new Handler();
    private FallingLine[] lines;
    private int endMatrixCount = 0;
    private boolean runningMatrix = false;

    // binary clock variables
    private int endClockCount = 0;
    private boolean runningClock = false;

    // isola game variables
    private int beginIsolaCount = 0;
    private boolean runningIsola = false;
    private boolean gameOver = false;
    private boolean playerTurn = true;
    private int playerX = BOARD_HEIGHT-1;
    private int playerY = BOARD_WIDTH/2;
    private int machineX = 0;
    private int machineY = BOARD_WIDTH/2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // disable the status bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // seizure warning dialog
        if (isFirstTime()) {
            new AlertDialog.Builder(this)
                    .setTitle("Seizure Warning")
                    .setMessage("This application uses rapid color changes.")
                    .setCancelable(false)
                    .setPositiveButton("Okay", null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();

        }

        setContentView(R.layout.activity_main);

        mPlayer = MediaPlayer.create(this, R.raw.satyr);
//        mPlayer.start();

        gameBoard = new View[BOARD_HEIGHT][BOARD_WIDTH];
        achievements = new boolean[8];
        layer = new int[5];
        lines = new FallingLine[7];
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout ll;

        // create a linear layout to hold columns of tiles
        LinearLayout container = (LinearLayout)findViewById(R.id.container);
        assert container != null;

        // add columns to container
        for (int i = 0; i < gameBoard.length; i++) {
            ll = new LinearLayout(this);
            ll.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
//                    1f
            );
            container.addView(ll, params);

            // add tiles to columns
            for (int j = 0; j < gameBoard[i].length; j++) {
                gameBoard[i][j] = inflater.inflate(R.layout.tile, ll, false);
                gameBoard[i][j].setTag(R.id.row, i);
                gameBoard[i][j].setTag(R.id.column, j);
                ll.addView(gameBoard[i][j]);
            }
        }
        addTileListeners();
        setColorPalette();
    }

    private boolean isFirstTime() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        boolean firstTime = preferences.getBoolean("FirstTime", false);

        if (!firstTime) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("FirstTime", true);
            editor.commit();
        }
        return !firstTime;
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mPlayer.pause();

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("Rippler", achievements[ACHIEVEMENT_RIPPLER]);
        editor.putBoolean("Pyrotechnician", achievements[ACHIEVEMENT_PYROTECHNICIAN]);
        editor.putBoolean("Explorer", achievements[ACHIEVEMENT_EXPLORER]);
        editor.putBoolean("ChosenOne", achievements[ACHIEVEMENT_CHOSEN_ONE]);
        editor.putBoolean("DestroyerOfWorlds", achievements[ACHIEVEMENT_DESTROYER_OF_WORLDS]);
        editor.putBoolean("MasterOfTime", achievements[ACHIEVEMENT_MASTER_OF_TIME]);
        editor.putBoolean("Gladiator", achievements[ACHIEVEMENT_GLADIATOR]);
        editor.putBoolean("Victor", achievements[ACHIEVEMENT_VICTOR]);
        editor.commit();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mPlayer.start();

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        achievements[ACHIEVEMENT_RIPPLER] = preferences.getBoolean("Rippler", false);
        achievements[ACHIEVEMENT_PYROTECHNICIAN] = preferences.getBoolean("Pyrotechnician", false);
        achievements[ACHIEVEMENT_EXPLORER] = preferences.getBoolean("Explorer", false);
        achievements[ACHIEVEMENT_CHOSEN_ONE] = preferences.getBoolean("ChosenOne", false);
        achievements[ACHIEVEMENT_DESTROYER_OF_WORLDS] = preferences.getBoolean("DestroyerOfWorlds", false);
        achievements[ACHIEVEMENT_MASTER_OF_TIME] = preferences.getBoolean("MasterOfTime", false);
        achievements[ACHIEVEMENT_GLADIATOR] = preferences.getBoolean("Gladiator", false);
        achievements[ACHIEVEMENT_VICTOR] = preferences.getBoolean("Victor", false);
    }

    // inform user when they achieved a goal
    public void announceAchievement(String message) {
        int count = 0;
        for (int i = 0, j = 0; i < achievements.length; i++) {
            if (achievements[i])
                count++;
        }
        message += " (" + count + "/" + achievements.length + ")";
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // add touch listener to each tile to detect short and long touches
    public void addTileListeners() {
        for (int i = 0; i < gameBoard.length; i++) {
            for (int j = 0; j < gameBoard[i].length; j++) {
                gameBoard[i][j].setOnTouchListener(new View.OnTouchListener() {
                    long startTime = 0;
                    long endTime = 0;

                    // short touch: normal pulse
                    // long touch: psychedelic pulse on release
                    public boolean onTouch(View v, MotionEvent event) {
                        final int x = (int) v.getTag(R.id.row);
                        final int y = (int) v.getTag(R.id.column);

                        // short touch
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            startTime = event.getEventTime();

                            if (runningIsola) {
                                processGameTouch(x, y);
                            }

                            // end matrix animation if 4 touch events occur
                            if (runningMatrix && endMatrixCount > 3) {
                                endMatrixCount = 0;
                                endMatrixDisplay();
                                return true;
                            } else if (runningMatrix) {
                                endMatrixCount++;
                                return true;
                            }

                            // end clock if 8 touch events occur, but change color palette after 4
                            if ((runningClock && endClockCount < 4) || (endClockCount > 4 && endClockCount < 8)) {
                                endClockCount++;
                                return true;
                            } else if (runningClock && endClockCount == 4) {
                                currentTheme = THEME_GRADIENT;
                                endClockCount++;
                                setColorPalette();
                                startBinaryClock();
                                return true;
                            } else if (endClockCount == 8) {
                                endClockCount = 0;
                                runningClock = false;
                                return true;
                            }

                            pickTheme(x, y);

                            if (themeChangeCount < SAME_TILE_THRESH)
                                pulse(false, x, y);
                            return true;
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            endTime = event.getEventTime();


                            // long touch, handler sends 4 pulses on runnables
                            // to the UI thread in quick succession, pulses include diagonals
                            if (endTime - startTime > 1000 && !runningIsola && !runningClock && !runningMatrix) {
                                if (!achievements[ACHIEVEMENT_PYROTECHNICIAN]) {
                                    achievements[ACHIEVEMENT_PYROTECHNICIAN] = true;
                                    announceAchievement(getResources().getString(R.string.pyrotechnician));
                                }

                                rapidDiagonalPulse(x, y);
                            }
                            return true;
                        } else {
                            return true;
                        }
                    }
                });
            }
        }
    }

    // set the currentTheme, which determines the color palette
    private void pickTheme(int x, int y) {
        // keep track of events to determine if UI skin should change
        if (prevX == x && prevY == y) {
            sameTileCount++;
        } else if (prevPrevX == x && prevPrevY == y) {
            alternatingTilesCount++;
        } else {
            sameTileCount = 0;
            alternatingTilesCount = 0;
        }

        // begin a game of isola if user presses all buttons on perimeter clockwise
        if (!runningIsola) {
            if (prevX == 0 && prevY == 0 && x == 0 && y == 1) {
                // top left corner
                beginIsolaCount++;
            } else if (prevX == 0 && prevY < BOARD_WIDTH - 1 && x == 0 && y - prevY == 1) {
                // top row rightward
                beginIsolaCount++;
            } else if (prevX == 0 && prevY == BOARD_WIDTH - 1 && x != 1 && y != BOARD_WIDTH - 1) {
                // top right corner
                beginIsolaCount = 0;
            } else if (prevY == BOARD_WIDTH - 1 && prevX < BOARD_HEIGHT - 1 && y == BOARD_WIDTH - 1 && x - prevX == 1) {
                // last column downward
                beginIsolaCount++;
            } else if (prevX == BOARD_HEIGHT - 1 && prevY == BOARD_WIDTH - 1 && x != BOARD_HEIGHT - 1 && y != BOARD_WIDTH - 2) {
                // bottom right corner
                beginIsolaCount = 1;
            } else if (prevX == BOARD_HEIGHT - 1 && prevY > 0 && x == BOARD_HEIGHT - 1 && prevY - y == 1) {
                // last row leftward
                beginIsolaCount++;
            } else if (prevX == BOARD_HEIGHT - 1 && prevY == 0 && x != BOARD_HEIGHT - 2 && y != 0) {
                // bottom left corner
                beginIsolaCount = 1;
            } else if (prevY == 0 && prevX > 0 && y == 0 && prevX - x == 1) {
                // first column upward
                beginIsolaCount++;
            } else {
                beginIsolaCount = 1;
            }

            // check if user completed full clockwise tour of perimeter
            if (beginIsolaCount == 2 * gameBoard.length + 2 * gameBoard[0].length - 4) {
                currentTheme = THEME_EARTH;
                setColorPalette();
                beginIsolaGame();
            }
        }

        // keep track of the 2 most recently touched tiles
        prevPrevX = prevX;
        prevPrevY = prevY;
        prevX = x;
        prevY = y;

        // change UI skin if a single tile is pressed 15 times in a row
        if (sameTileCount == SAME_TILE_THRESH && currentTheme == THEME_LIGHT) {
            currentTheme = THEME_DARK;
            setColorPalette();
            sameTileCount = 0;
            themeChangeCount++;

            // UI skin change is an achievement
            if (!achievements[ACHIEVEMENT_EXPLORER]) {
                achievements[ACHIEVEMENT_EXPLORER] = true;
                announceAchievement(getResources().getString(R.string.explorer));
            }
        } else if (sameTileCount == SAME_TILE_THRESH && currentTheme == THEME_DARK) {
            currentTheme = THEME_LIGHT;
            setColorPalette();
            sameTileCount = 0;
            themeChangeCount++;
        } else if (sameTileCount == SAME_TILE_THRESH && currentTheme == THEME_EARTH) {
            currentTheme = THEME_LIGHT;
            setColorPalette();
            sameTileCount = 0;
        } else if (sameTileCount == SAME_TILE_THRESH && currentTheme == THEME_GRADIENT) {
            currentTheme = THEME_LIGHT;
            setColorPalette();
            sameTileCount = 0;
        }

        // start the matrix animation if the user alternates touch between 2 tiles long enough
        if (alternatingTilesCount == ALTERNATING_TILE_THRESH) {
            alternatingTilesCount = 0;
            matrixBlocks();
        }

        // begin binary clock if user changes UI skins 15 times
        if (themeChangeCount == THEME_CHANGE_THRESH) {
            currentTheme = THEME_DARK; // use the dark theme because it looks nice
            setColorPalette();
            themeChangeCount = 0;
            startBinaryClock();
        }
    }

    // define colors of current game skin
    private void setColorPalette() {
        if (currentTheme == THEME_LIGHT) {
            findViewById(R.id.container).setBackgroundColor(0xFFFFFFFF);
            layer[0] = 0xFF3FE1FA;
            layer[1] = 0xFFFF0000;
            layer[2] = 0xFFFFFF00;
            layer[3] = 0xFFFF0DFF;
            layer[4] = 0xFF1DFF0F;
        } else if (currentTheme == THEME_DARK) {
            findViewById(R.id.container).setBackgroundColor(0xFF000000);
            layer[0] = 0xFF39FF14;
            layer[1] = 0xFF000000;
            layer[2] = 0XFF668B8B;
            layer[4] = 0xFFFF0000;
            layer[3] = 0xFF3FE1FA;
        } else if (currentTheme == THEME_GRADIENT) {
            findViewById(R.id.container).setBackgroundColor(0xFFCCCCCC);
            layer[0] = 0x00000000;
            layer[1] = 0x44000000;
            layer[2] = 0x88000000;
            layer[3] = 0xDD000000;
            layer[4] = 0xFF000000;
        } else {
            findViewById(R.id.container).setBackgroundColor(0xFFC98910);
            layer[0] = 0xFFD9A441;
            layer[1] = 0xFFCCC2C2;
            layer[2] = 0xFF965A38;
            layer[3] = 0xFFA8A8A8;
            layer[4] = 0xFFC98910;
        }
    }


    // PULSE ANIMATION
    /*--------------------------------------------------------------------------------------------*/

    // pulse wrapper fo rapid-pulse firework effect
    private void rapidDiagonalPulse(int x, int y) {
        final int X = x;
        final int Y = y;

        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            int count = 0;

            public void run() {
                pulse(true, X, Y);
                count++;

                if (count < 4)
                    handler.postDelayed(this, 80);
                else
                    handler.removeCallbacks(this);
            }
        };
        handler.post(runnable);
    }

    // send color waves, i.e. a pulse, orthogonally away from the touched tile
    public void pulse(boolean diagonal, int xCoord, int yCoord) {

        // the first pulse event is an achievement
        if (!achievements[ACHIEVEMENT_RIPPLER]) {
            achievements[ACHIEVEMENT_RIPPLER] = true;
            announceAchievement(getResources().getString(R.string.rippler));
        }

        // utility variables
        final boolean diag = diagonal;
        final int x = xCoord;
        final int y = yCoord;

        // animate a pulse when user presses a tile
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            int count = 1;
            public void run() {
                // don't pulse for animation events
                if (runningIsola || runningClock || runningMatrix) return;

                boolean done = true;

                // pulse upwards
                if (x - count >= 0) {
                    done = false;
                    ColorDrawable drawable = (ColorDrawable)gameBoard[x-count][y].getBackground();
                    int color = drawable.getColor();

                    if (color == layer[1])
                        gameBoard[x-count][y].setBackgroundColor(layer[2]);
                    else if (color == layer[2])
                        gameBoard[x-count][y].setBackgroundColor(layer[3]);
                    else if (color == layer[3])
                        gameBoard[x-count][y].setBackgroundColor(layer[4]);
                    else
                        gameBoard[x-count][y].setBackgroundColor(layer[1]);

                    gameBoard[x-count+1][y].setBackgroundColor(layer[0]);
                } else if (x - count == -1) {
                    gameBoard[x-count+1][y].setBackgroundColor(layer[0]);
                }

                // pulse downwards
                if (x + count < gameBoard.length) {
                    done = false;
                    ColorDrawable drawable = (ColorDrawable)gameBoard[x+count][y].getBackground();
                    int color = drawable.getColor();

                    if (color == layer[1])
                        gameBoard[x+count][y].setBackgroundColor(layer[2]);
                    else if (color == layer[2])
                        gameBoard[x+count][y].setBackgroundColor(layer[3]);
                    else if (color == layer[3])
                        gameBoard[x+count][y].setBackgroundColor(layer[4]);
                    else
                        gameBoard[x+count][y].setBackgroundColor(layer[1]);

                    gameBoard[x+count-1][y].setBackgroundColor(layer[0]);
                } else if (x + count == gameBoard.length) {
                    gameBoard[x+count-1][y].setBackgroundColor(layer[0]);
                }

                // pulse leftwards
                if (y - count >= 0) {
                    done = false;
                    ColorDrawable drawable = (ColorDrawable)gameBoard[x][y-count].getBackground();
                    int color = drawable.getColor();

                    if (color == layer[1])
                        gameBoard[x][y-count].setBackgroundColor(layer[2]);
                    else if (color == layer[2])
                        gameBoard[x][y-count].setBackgroundColor(layer[3]);
                    else if (color == layer[3])
                        gameBoard[x][y-count].setBackgroundColor(layer[4]);
                    else
                        gameBoard[x][y-count].setBackgroundColor(layer[1]);
                    gameBoard[x][y-count+1].setBackgroundColor(layer[0]);
                } else if (y - count == -1) {
                    gameBoard[x][y-count+1].setBackgroundColor(layer[0]);
                }

                // pulse rightwards
                if (y + count < 7) {
                    done = false;
                    ColorDrawable drawable = (ColorDrawable)gameBoard[x][y+count].getBackground();
                    int color = drawable.getColor();

                    if (color == layer[1])
                        gameBoard[x][y+count].setBackgroundColor(layer[2]);
                    else if (color == layer[2])
                        gameBoard[x][y+count].setBackgroundColor(layer[3]);
                    else if (color == layer[3])
                        gameBoard[x][y+count].setBackgroundColor(layer[4]);
                    else
                        gameBoard[x][y+count].setBackgroundColor(layer[1]);

                    gameBoard[x][y+count-1].setBackgroundColor(layer[0]);
                } else if (y + count == 7) {
                    gameBoard[x][y+count-1].setBackgroundColor(layer[0]);
                }

                if (diag) {
                    // pulse diagonally down and rightwards
                    if (x + count < gameBoard.length && y + count < 7) {
                        done = false;
                        ColorDrawable drawable = (ColorDrawable)gameBoard[x+count][y+count].getBackground();
                        int color = drawable.getColor();

                        if (color == layer[1])
                            gameBoard[x+count][y+count].setBackgroundColor(layer[2]);
                        else if (color == layer[2])
                            gameBoard[x+count][y+count].setBackgroundColor(layer[3]);
                        else if (color == layer[3])
                            gameBoard[x+count][y+count].setBackgroundColor(layer[4]);
                        else
                            gameBoard[x+count][y+count].setBackgroundColor(layer[1]);

                        gameBoard[x+count-1][y+count-1].setBackgroundColor(layer[0]);
                    } else if (x + count <= gameBoard.length && y + count <= 7) {
                        gameBoard[x+count-1][y+count-1].setBackgroundColor(layer[0]);
                    }

                    // pulse diagonally down and leftwards
                    if (x + count < gameBoard.length && y - count >= 0) {
                        done = false;
                        ColorDrawable drawable = (ColorDrawable)gameBoard[x+count][y-count].getBackground();
                        int color = drawable.getColor();

                        if (color == layer[1])
                            gameBoard[x+count][y-count].setBackgroundColor(layer[2]);
                        else if (color == layer[2])
                            gameBoard[x+count][y-count].setBackgroundColor(layer[3]);
                        else if (color == layer[3])
                            gameBoard[x+count][y-count].setBackgroundColor(layer[4]);
                        else
                            gameBoard[x+count][y-count].setBackgroundColor(layer[1]);

                        gameBoard[x+count-1][y-count+1].setBackgroundColor(layer[0]);
                    } else if (x + count <= gameBoard.length && y - count >= -1) {
                        gameBoard[x+count-1][y-count+1].setBackgroundColor(layer[0]);
                    }

                    // pulse diagonally up and leftwards
                    if (x - count >= 0 && y - count >= 0) {
                        done = false;
                        ColorDrawable drawable = (ColorDrawable)gameBoard[x-count][y-count].getBackground();
                        int color = drawable.getColor();

                        if (color == layer[1])
                            gameBoard[x-count][y-count].setBackgroundColor(layer[2]);
                        else if (color == layer[2])
                            gameBoard[x-count][y-count].setBackgroundColor(layer[3]);
                        else if (color == layer[3])
                            gameBoard[x-count][y-count].setBackgroundColor(layer[4]);
                        else
                            gameBoard[x-count][y-count].setBackgroundColor(layer[1]);

                        gameBoard[x-count+1][y-count+1].setBackgroundColor(layer[0]);
                    } else if (x - count >= -1 && y - count >= -1) {
                        gameBoard[x-count+1][y-count+1].setBackgroundColor(layer[0]);
                    }

                    // pulse diagonally up and rightwards
                    if (x - count >= 0 && y + count < 7) {
                        done = false;
                        ColorDrawable drawable = (ColorDrawable)gameBoard[x-count][y+count].getBackground();
                        int color = drawable.getColor();

                        if (color == layer[1])
                            gameBoard[x-count][y+count].setBackgroundColor(layer[2]);
                        else if (color == layer[2])
                            gameBoard[x-count][y+count].setBackgroundColor(layer[3]);
                        else if (color == layer[3])
                            gameBoard[x-count][y+count].setBackgroundColor(layer[4]);
                        else
                            gameBoard[x-count][y+count].setBackgroundColor(layer[1]);

                        gameBoard[x-count+1][y+count-1].setBackgroundColor(layer[0]);
                    } else if (x - count >= -1 && y + count <= 7) {
                        gameBoard[x-count+1][y+count-1].setBackgroundColor(layer[0]);
                    }
                }

                // continue propagating outward
                count++;
                if (!done)
                    handler.postDelayed(this, 350);
                else
                    handler.removeCallbacks(this);
            }
        };
        handler.post(runnable);
    }


    // ISOLA 2-PLAYER STRATEGY GAME
    /*--------------------------------------------------------------------------------------------*/

    // start the isola game by setting up the board
    private void beginIsolaGame() {

        // flag touches to be processed as game input
        runningIsola = true;

        // reaching isola is an achievement
        if (!achievements[ACHIEVEMENT_GLADIATOR]) {
            achievements[ACHIEVEMENT_GLADIATOR] = true;
            announceAchievement(getResources().getString(R.string.gladiator));
        }

        // set up (i.e. paint) the board for play
        for (int i = 0; i < gameBoard.length; i++) {
            for (int j = 0; j < gameBoard[i].length; j++) {
                gameBoard[i][j].setBackgroundColor(layer[3]);
            }
        }
        gameBoard[playerX][playerY].setBackgroundColor(blue);
        gameBoard[machineX][machineY].setBackgroundColor(red);
    }

    private void processGameTouch(int x, int y) {

        // only continue processing a touch event if it represents a legal move
        if (isLegalMove(x, y, playerTurn)) {

            // perform the move
            if (playerTurn) {
                gameBoard[x][y].setBackgroundColor(blue);
                gameBoard[playerX][playerY].setBackgroundColor(layer[4]);
                playerX = x;
                playerY = y;
            } else {
                gameBoard[x][y].setBackgroundColor(red);
                gameBoard[machineX][machineY].setBackgroundColor(layer[4]);
                machineX = x;
                machineY = y;
            }
            playerTurn = !playerTurn;

            // check if any legal moves are currently available; if not, terminate game
            if (checkGameOver(playerTurn)) {
                runningIsola = false;

                if (!playerTurn && !achievements[ACHIEVEMENT_VICTOR]) {
                    achievements[ACHIEVEMENT_VICTOR] = true;
                    announceAchievement(getResources().getString(R.string.victor));
                    rapidDiagonalPulse(machineX, machineY);
                } else if (!playerTurn) {
                    Toast.makeText(this, getResources().getString(R.string.you_win),
                            Toast.LENGTH_LONG).show();
                    rapidDiagonalPulse(machineX, machineY);
                } else {
                    Toast.makeText(this, getResources().getString(R.string.you_lose),
                            Toast.LENGTH_LONG).show();
                    rapidDiagonalPulse(playerX, playerY);
                }

                // reset game variables for next time
                gameOver = false;
                playerTurn = true;
                playerX = BOARD_HEIGHT-1;
                playerY = BOARD_WIDTH/2;
                machineX = 0;
                machineY = BOARD_WIDTH/2;
                return;
            }
        }
    }

    // checks if a move is legal
    private boolean isLegalMove(int x, int y, boolean playerTurn) {
        if (x < 0 || x > BOARD_HEIGHT-1 || y < 0 || y > BOARD_WIDTH-1) {
            return false;
        }

        ColorDrawable drawable = (ColorDrawable)gameBoard[x][y].getBackground();
        int color = drawable.getColor();

        if (x == playerX && y == playerY || x == machineX && y == machineY || color == layer[4]) {
            return false;
        }

        if (playerTurn) {
            // unless this square is adjacent to the player, it's illegal
            if (x - 1 == playerX && y - 1 == playerY) {
                return true;
            } else if (x - 1 == playerX && y == playerY) {
                return true;
            } else if (x - 1 == playerX && y + 1 == playerY) {
                return true;
            } else if (x == playerX && y + 1 == playerY) {
                return true;
            } else if (x + 1 == playerX && y + 1 == playerY) {
                return true;
            } else if (x + 1 == playerX  && y == playerY) {
                return true;
            } else if (x + 1 == playerX && y - 1 == playerY) {
                return true;
            } else if (x == playerX && y - 1 == playerY) {
                return true;
            }
            return false;
        } else {
            // unless this square is adjacent to the ai , it's illegal
            if (x - 1 == machineX && y - 1 == machineY) {
                return true;
            } else if (x - 1 == machineX && y == machineY) {
                return true;
            } else if (x - 1 == machineX && y + 1 == machineY) {
                return true;
            } else if (x == machineX && y + 1 == machineY) {
                return true;
            } else if (x + 1 == machineX && y + 1 == machineY) {
                return true;
            } else if (x + 1 == machineX  && y == machineY) {
                return true;
            } else if (x + 1 == machineX && y - 1 == machineY) {
                return true;
            } else if (x == machineX && y - 1 == machineY) {
                return true;
            }
            return false;
        }
    }

    // determines if the player or ai has any legal move
    private boolean checkGameOver(boolean playerTurn) {
        if (playerTurn) {
            if (isLegalMove(playerX - 1, playerY - 1, playerTurn)) {
                return false;
            } else if (isLegalMove(playerX - 1, playerY, playerTurn)) {
                return false;
            } else if (isLegalMove(playerX - 1, playerY + 1, playerTurn)) {
                return false;
            } else if (isLegalMove(playerX, playerY + 1, playerTurn)) {
                return false;
            } else if (isLegalMove(playerX + 1, playerY + 1, playerTurn)) {
                return false;
            } else if (isLegalMove(playerX + 1, playerY, playerTurn)) {
                return false;
            } else if (isLegalMove(playerX + 1, playerY - 1, playerTurn)) {
                return false;
            } else if (isLegalMove(playerX, playerY - 1, playerTurn)) {
                return false;
            }
        } else {
            if (isLegalMove(machineX - 1, machineY - 1, playerTurn)) {
                return false;
            } else if (isLegalMove(machineX - 1, machineY, playerTurn)) {
                return false;
            } else if (isLegalMove(machineX - 1, machineY + 1, playerTurn)) {
                return false;
            } else if (isLegalMove(machineX, machineY + 1, playerTurn)) {
                return false;
            } else if (isLegalMove(machineX + 1, machineY + 1, playerTurn)) {
                return false;
            } else if (isLegalMove(machineX + 1, machineY, playerTurn)) {
                return false;
            } else if (isLegalMove(machineX + 1, machineY - 1, playerTurn)) {
                return false;
            } else if (isLegalMove(machineX, machineY - 1, playerTurn)) {
                return false;
            }
        }
        return true;
    }

    // BINARY CLOCK
    /*--------------------------------------------------------------------------------------------*/

    // start the binary clock
    private void startBinaryClock() {
        runningClock = true;

        // stop user input to allow user to see the animation
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        // starting the clock is an achievement
        if (!achievements[ACHIEVEMENT_MASTER_OF_TIME]) {
            achievements[ACHIEVEMENT_MASTER_OF_TIME] = true;
            announceAchievement(getResources().getString(R.string.master_of_time));
        }

        // reset tiles to be theme layer 0
        for (int i = 0; i < gameBoard.length; i++) {
            for (int j = 0; j < gameBoard[j].length; j++) {
                gameBoard[i][j].setBackgroundColor(layer[0]);
            }
        }

        // start the clock!
        final Handler binaryClockHandler = new Handler();
        binaryClockHandler.post(new Runnable() {
            @Override
            public void run() {
                if (runningClock) {
                    updateClock();
                    binaryClockHandler.postDelayed(this, 500);
                } else {
                    binaryClockHandler.removeCallbacks(this);
                }
            }
        });

        // resume user input after 3 seconds
        Runnable resumeInput = new Runnable() {
            @Override
            public void run() {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            }
        };
        lineHandler.postDelayed(resumeInput, 4000);
    }

    // get the current time and display it in the binary clock
    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        /*
        hourA -> gameBoard[1][1-2]
        hourB -> gameBoard[2][1-4]

        minuteA -> gameBoard[4][1-4]
        minuteB -> gameBoard[5][1-4]

        secondA -> gameBoard[7][1-4]
        secondB -> gameBoard[8][1-4]
         */

        // get the hour, assign the first digit A, and the second B
        int hour = cal.get(cal.HOUR_OF_DAY);
        int hourA = 0;
        int hourB;
        if (hour > 10) {
            hourB = hour % 10;
            hourA = (hour - hourB) / 10;
        } else {
            hourB = hour;
        }

        // get the minute
        int minute = cal.get(cal.MINUTE);
        int minuteA = 0;
        int minuteB;
        if (minute > 9) {
            minuteB = minute % 10;
            minuteA = (minute - minuteB) / 10;
        } else {
            minuteB = minute;
        }

        // get the second
        int second = cal.get(cal.SECOND);
        int secondA = 0;
        int secondB;
        if (second > 9) {
            secondB = second % 10;
            secondA = (second - secondB) / 10;
        } else {
            secondB = second;
        }

        paintFormerDigit(1, hourA);
        paintLatterDigit(2, hourB);
        paintFormerDigit(5, minuteA);
        paintLatterDigit(6, minuteB);
        paintFormerDigit(9, secondA);
        paintLatterDigit(10, secondB);
    }

    // continuously repaint the board to show the progression of time
    private void paintFormerDigit(int row, int digitValue) {
        if (row == 1) {
            if (digitValue == 0) {
                gameBoard[1][1].setBackgroundColor(layer[1]);
                gameBoard[1][2].setBackgroundColor(layer[1]);
            } else if (digitValue == 1) {
                gameBoard[1][1].setBackgroundColor(layer[1]);
                gameBoard[1][2].setBackgroundColor(layer[4]);
            } else {
                gameBoard[1][1].setBackgroundColor(layer[4]);
                gameBoard[1][2].setBackgroundColor(layer[4]);
            }
        } else {
            switch(digitValue) {
                case 0:
                    gameBoard[row][1].setBackgroundColor(layer[1]);
                    gameBoard[row][2].setBackgroundColor(layer[1]);
                    gameBoard[row][3].setBackgroundColor(layer[1]);
                    break;
                case 1:
                    gameBoard[row][1].setBackgroundColor(layer[1]);
                    gameBoard[row][2].setBackgroundColor(layer[1]);
                    gameBoard[row][3].setBackgroundColor(layer[4]);
                    break;
                case 2:
                    gameBoard[row][1].setBackgroundColor(layer[1]);
                    gameBoard[row][2].setBackgroundColor(layer[4]);
                    gameBoard[row][3].setBackgroundColor(layer[1]);
                    break;
                case 3:
                    gameBoard[row][1].setBackgroundColor(layer[1]);
                    gameBoard[row][2].setBackgroundColor(layer[4]);
                    gameBoard[row][3].setBackgroundColor(layer[4]);
                    break;
                case 4:
                    gameBoard[row][1].setBackgroundColor(layer[4]);
                    gameBoard[row][2].setBackgroundColor(layer[1]);
                    gameBoard[row][3].setBackgroundColor(layer[1]);
                    break;
                case 5:
                    gameBoard[row][1].setBackgroundColor(layer[4]);
                    gameBoard[row][2].setBackgroundColor(layer[1]);
                    gameBoard[row][3].setBackgroundColor(layer[4]);
                    break;
                case 6:
                    gameBoard[row][1].setBackgroundColor(layer[4]);
                    gameBoard[row][2].setBackgroundColor(layer[4]);
                    gameBoard[row][3].setBackgroundColor(layer[1]);
                    break;
            }
        }
    }

    // continuously repaint the board to show the progression of time
    private void paintLatterDigit(int row, int digitValue) {
        switch (digitValue) {
            case 0:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[1]);
                gameBoard[row][3].setBackgroundColor(layer[1]);
                gameBoard[row][4].setBackgroundColor(layer[1]);
                break;
            case 1:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[1]);
                gameBoard[row][3].setBackgroundColor(layer[1]);
                gameBoard[row][4].setBackgroundColor(layer[4]);
                break;
            case 2:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[1]);
                gameBoard[row][3].setBackgroundColor(layer[4]);
                gameBoard[row][4].setBackgroundColor(layer[1]);
                break;
            case 3:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[1]);
                gameBoard[row][3].setBackgroundColor(layer[4]);
                gameBoard[row][4].setBackgroundColor(layer[4]);
                break;
            case 4:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[4]);
                gameBoard[row][3].setBackgroundColor(layer[1]);
                gameBoard[row][4].setBackgroundColor(layer[1]);
                break;
            case 5:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[4]);
                gameBoard[row][3].setBackgroundColor(layer[1]);
                gameBoard[row][4].setBackgroundColor(layer[4]);
                break;
            case 6:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[4]);
                gameBoard[row][3].setBackgroundColor(layer[4]);
                gameBoard[row][4].setBackgroundColor(layer[1]);
                break;
            case 7:
                gameBoard[row][1].setBackgroundColor(layer[1]);
                gameBoard[row][2].setBackgroundColor(layer[4]);
                gameBoard[row][3].setBackgroundColor(layer[4]);
                gameBoard[row][4].setBackgroundColor(layer[4]);
                break;
            case 8:
                gameBoard[row][1].setBackgroundColor(layer[4]);
                gameBoard[row][2].setBackgroundColor(layer[1]);
                gameBoard[row][3].setBackgroundColor(layer[1]);
                gameBoard[row][4].setBackgroundColor(layer[1]);
                break;
            case 9:
                gameBoard[row][1].setBackgroundColor(layer[4]);
                gameBoard[row][2].setBackgroundColor(layer[1]);
                gameBoard[row][3].setBackgroundColor(layer[1]);
                gameBoard[row][4].setBackgroundColor(layer[4]);
                break;
        }
    }

    // MATRIX FALLING LINES ANIMATION
    /*--------------------------------------------------------------------------------------------*/

    // runnables to be used in the matrix animation
    private FallingLine[] pickFallingLineRunnables() {
        lines[0] = new FallingLine(0);
        lines[1] = new FallingLine(1);
        lines[2] = new FallingLine(2);
        lines[3] = new FallingLine(3);
        lines[4] = new FallingLine(4);
        lines[5] = new FallingLine(5);
        lines[6] = new FallingLine(6);
        return lines;
    }

    // stop the animation
    private void endMatrixDisplay() {
        lineHandler.removeCallbacks(lines[0]);
        lineHandler.removeCallbacks(lines[1]);
        lineHandler.removeCallbacks(lines[2]);
        lineHandler.removeCallbacks(lines[3]);
        lineHandler.removeCallbacks(lines[4]);
        lineHandler.removeCallbacks(lines[5]);
        lineHandler.removeCallbacks(lines[6]);
        runningMatrix = false;
        currentTheme = THEME_GRADIENT;
        setColorPalette();

        // reaching the gradient skin is an achievement
        if (!achievements[ACHIEVEMENT_DESTROYER_OF_WORLDS]) {
            achievements[ACHIEVEMENT_DESTROYER_OF_WORLDS] = true;
            announceAchievement(getResources().getString(R.string.destroyer_of_worlds));
        }
    }

    // the animation itself
    private void matrixBlocks() {
        runningMatrix = true;

        // stop user input to allow user to see the animation
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        // award user achievement
        if (!achievements[ACHIEVEMENT_CHOSEN_ONE]) {
            achievements[ACHIEVEMENT_CHOSEN_ONE] = true;
            announceAchievement(getResources().getString(R.string.chosen_one));
        }

        // use the dark currentTheme for full effect
        currentTheme = THEME_DARK;
        setColorPalette();

        // hide all tiles
        for (int i = 0; i < gameBoard.length; i++) {
            for (int j = 0; j < gameBoard[i].length; j++) {
                gameBoard[i][j].setBackgroundColor(layer[1]);
            }
        }

        // start the lines a-fallin'
        FallingLine[] lines = pickFallingLineRunnables();
        Random rand = new Random();
        lineHandler.postDelayed(lines[0], rand.nextInt(200));
        lineHandler.postDelayed(lines[1], rand.nextInt((1800-200) + 200));
        lineHandler.postDelayed(lines[2], rand.nextInt((800-400) + 400));
        lineHandler.postDelayed(lines[3], rand.nextInt((3800-600) + 600));
        lineHandler.postDelayed(lines[4], rand.nextInt((4800-800) + 800));
        lineHandler.postDelayed(lines[5], rand.nextInt((5800-1200) + 1200));
        lineHandler.postDelayed(lines[6], rand.nextInt((6800-1500) + 1500));

        // resume user input after 3 seconds
        Runnable resumeInput = new Runnable() {
            @Override
            public void run() {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            }
        };
        lineHandler.postDelayed(resumeInput, 4000);
    }

    // nested class for line falling animations, matrix style
    private class FallingLine implements Runnable {
        // utility variables
        Random rand = new Random();
        int white = 0xFFCCCCCC;
        int colNumber;
        int count = 0;

        // constructor
        public FallingLine(int colNumber) {
            this.colNumber = colNumber;
        }

        // animate a line falling down a column
        public void run() {

            // paint the leading square white and the one behind it layer 1 of the palette
            if (count < BOARD_HEIGHT && count > 0) {
                gameBoard[count][colNumber].setBackgroundColor(white);
                gameBoard[count-1][colNumber].setBackgroundColor(layer[0]);
            } else if (count == 0) {
                gameBoard[count][colNumber].setBackgroundColor(white);
            }

            // paint the tiles background color, effectively removing the line
            if (count > 4 && count < BOARD_HEIGHT+5) {
                gameBoard[count-5][colNumber].setBackgroundColor(layer[1]);
            }
            count++;

            // continue down the column
            if (count < BOARD_HEIGHT+5) {
                lineHandler.postDelayed(this, 100);
            } else {
                count = 0;
                lineHandler.postDelayed(this, rand.nextInt(1700-500)+500);
            }
        }
    }

    /*--------------------------------------------------------------------------------------------*/
}
