package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.controllers.examples.Legacy;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.DM;

import java.util.Random;

import java.util.HashSet;
import java.util.ArrayList;

import java.util.EnumMap;

public class BeamSearcher extends Controller<MOVE>{
    private MOVE[] allMoves = MOVE.values();
    private Legacy lg = new Legacy();
    private int C = 3;
    private int K = 4;

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
	  Generate random sequences of C integers between 0 and 5, inclusive
	  For each:
	    Until the last number is reached, call current number c
	    If dead, stop
	    If there is only one possible move, obey it
	    If there are two, pick the one denoted by c%2
	    If three, pick the one denoted by c%3	  
 
	  Until we run out of time
	    Explore all children of all sequences: add moves to each
	    Find the K best successor sequences/states
	*/

	class BeamEntry{
	    public Game state;
	    public MOVE firstMove;
	    public int score;
	    BeamEntry(Game g, MOVE m, int i){
		state = g;
		MOVE = m;
		score = i;
	    }
	};

	ArrayList<BeamEntry> beam = new ArrayList<BeamEntry>();
	for (int j = 0; j < K; j++){
	    ArrayList<Integer> choices = new ArrayList<Integer>();
	    for(int i = 0; i < C; i++){ choices.add(rnd.nextInt(6)); }

	    Game copy = game.copy();
	    int c = 0;
	    MOVE firstMove = MOVE.NEUTRAL;

	    //Use the moves...
	    while(true){
		if (c==0) { firstMove = m; }

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
		    m = possible[choices.get(c++) % possible.length];
		}
		
		copy.advanceGame(m, lg.getMove(copy, System.currentTimeMillis()+1));
	    }
	    
	    int s = score(copy);
	    BeamEntry be = new BeamEntry(copy,firstMove,s);
	    beam.add(be);
	}

	int bestScore = Integer.MIN_VALUE;
	MOVE bestMove = MOVE.NEUTRAL;

	//Loop until we run out of time
	while (timeDue-1 > System.currentTimeMillis()){

	}

	return bestMove;
    }
}
