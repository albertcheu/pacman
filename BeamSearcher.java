package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.controllers.examples.Legacy;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;
import pacman.game.Constants.DM;

import java.util.Random;
import java.util.Collections;
import java.util.Comparator;
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
	long gap = (timeDue - System.currentTimeMillis());

	/*
	  Generate random sequences of MOVES/states
 
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
		firstMove = m;
		score = i;
	    }
	};

	class CustomComparator implements Comparator<BeamEntry>{
	    public int compare(BeamEntry be1, BeamEntry be2){
		return be2.score-be1.score;
	    }
	}

	ArrayList<BeamEntry> beam = new ArrayList<BeamEntry>();

	for (int i = 0; i < K; i++){
	    Game copy = game.copy();
	    MOVE firstMove = MOVE.NEUTRAL;

	    for(int j = 0; j < C; j++){
		if (copy.wasPacManEaten()){ break; }
		MOVE m = allMoves[rnd.nextInt(4)];
		if (j == 0) { firstMove = m; }
		copy.advanceGame(m, lg.getMove(copy, System.currentTimeMillis()+1));
	    }

	    int s = score(copy);
	    BeamEntry be = new BeamEntry(copy,firstMove,s);
	    //System.out.println(be.firstMove);
	    beam.add(be);
	}

	Collections.sort(beam,new CustomComparator());
	MOVE ans = beam.get(0).firstMove;

	while (timeDue-K > System.currentTimeMillis()){
	//for (int c = 0; c < 10; c++){

	    int min = K;
	    if (beam.size() < min) { min = beam.size(); }

	    //To hold child states
	    ArrayList<BeamEntry> children = new ArrayList<BeamEntry>();

	    //
	    for(int i = 0; i < min; i++){
		BeamEntry be = beam.get(i);
		if (be.state.wasPacManEaten()) { continue; }
		int pnode = be.state.getPacmanCurrentNodeIndex();
		MOVE[] moves = be.state.getPossibleMoves(pnode);
		for (int j = 0; j < moves.length; j++){
		    Game copy = be.state.copy();
		    copy.advanceGame(moves[j],
				     lg.getMove(copy, System.currentTimeMillis()+1));
		    int s = score(copy);
		    children.add(new BeamEntry(copy,be.firstMove,s));
		}
	    }

	    Collections.sort(children,new CustomComparator());

	    beam.clear();

	    if (K > children.size()) { min = children.size(); }
	    for(int i = 0; i < min; i++){
		beam.add(children.get(i));
	    }
	}

	try { return beam.get(0).firstMove; }
	catch (Exception e) {}
	return ans;
	//return MOVE.UP;
    }
}
