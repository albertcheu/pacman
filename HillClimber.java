package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.controllers.examples.Legacy;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.DM;

import java.util.Random;
import java.util.ArrayList;

import java.util.EnumMap;

public class HillClimber extends Controller<MOVE>{
    private MOVE[] allMoves = MOVE.values();
    private Legacy lg = new Legacy();
    private int C = 4;

    private Random rnd=new Random();

    //Evaluates game state
    private int score(Game state){
	if (state.wasPacManEaten()) { return Integer.MIN_VALUE; }
	int pnode = state.getPacmanCurrentNodeIndex();
	int shortest2inedible = Integer.MAX_VALUE;
	int shortest2edible = Integer.MAX_VALUE;

	//Find distances to ghosts
	for(GHOST g: GHOST.values()){
	    int gnode = state.getGhostCurrentNodeIndex(g);
	    int dist = state.getShortestPathDistance(pnode,gnode);
	    if (state.isGhostEdible(g) && dist < shortest2edible)
		{ shortest2edible = dist; }
	    else if (!state.isGhostEdible(g) && dist < shortest2inedible)
		{ shortest2inedible = dist; }
	}

	int shortest2pill = Integer.MAX_VALUE;
	//Find distances to pills
	for(int i: state.getActivePillsIndices()){
	    int dist = state.getShortestPathDistance(pnode,i);
	    if (dist < shortest2pill) { shortest2pill = dist; }
	}

	//Complete evaluation based on ghosts & score
	if (shortest2edible == Integer.MAX_VALUE) { shortest2edible = 0; }
	else if (shortest2inedible == Integer.MAX_VALUE) { shortest2inedible = 0; }
	return state.getScore() + shortest2inedible - shortest2edible - shortest2pill;
    }


    public MOVE getMove(Game game, long timeDue) {
	/*
	  Generate random sequence of C integers between 0 and 5, inclusive
	  Until the last number is reached, call current number c
	  If dead, stop
	  If there is only one possible move, obey it
	  If there are two, pick the one denoted by c%2
	  If three, pick the one denoted by c%3
	  Evaluate
	  Change one random move and repeat
	*/

	ArrayList<Integer> choices = new ArrayList<Integer>();
	for(int i = 0; i < C; i++){ choices.add(rnd.nextInt(6)); }

	int bestScore = Integer.MIN_VALUE;
	MOVE bestMove = MOVE.NEUTRAL;

	//Loop until we run out of time
	while (timeDue-4 > System.currentTimeMillis()){
	    Game copy = game.copy();

	    //Make a random change to our move sequence
	    int index = rnd.nextInt(C);
	    int oldVal = choices.get(index);
	    int newVal = rnd.nextInt(6);
	    choices.set(index,newVal);
	    
	    int c = 0;
	    MOVE firstMove = MOVE.NEUTRAL;
	    boolean first = true;

	    //Use the moves...
	    while(true){
		//...Until we die
		if (copy.wasPacManEaten()){ break; }

		//Find where we are
		int pnode = copy.getPacmanCurrentNodeIndex();

		MOVE lastMove = copy.getPacmanLastMoveMade();
		MOVE[] possible = copy.getPossibleMoves(pnode, lastMove);
		MOVE m = possible[0];
		//Make a turn
		if (possible.length > 1) {
		    if (c == C) { break; }
		    //Permit backtracking upon hitting a junction
		    //possible = copy.getPossibleMoves(pnode);
		    m = possible[choices.get(c++) % possible.length];
		}
		
		copy.advanceGame(m, lg.getMove(copy, System.currentTimeMillis()+1));

		if (first) { firstMove = m; first = false; }		
	    }
	    
	    int s = score(copy);
	    if (s > bestScore) {
		bestMove = firstMove;
		bestScore = s;
	    }
	    else { choices.set(index,oldVal); }
	}

	return bestMove;
    }
}
