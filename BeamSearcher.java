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
import java.util.ArrayList;

public class BeamSearcher extends Controller<MOVE>{
    private MOVE[] allMoves = MOVE.values();
    private Legacy lg = new Legacy();
    private int C = 4;
    private int K = 10;

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

    private MOVE progress(ArrayList<Integer> choices, Game copy){
	int c = 0;
	MOVE firstMove = MOVE.NEUTRAL;
	boolean first = true;
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
		if (c == choices.size()) { break; }
		m = possible[choices.get(c++) % possible.length];
	    }

	    copy.advanceGame(m, lg.getMove(copy, System.currentTimeMillis()+1));
	    if (first) { firstMove = m; first = false; }
	}
	return firstMove;
    }

    public MOVE getMove(Game game, long timeDue) {

	/*
	  Generate K random sequences of C integers between 0 and 5, inclusive
	  Until the last number is reached, call current number c
	  If dead, stop
	  If there is only one possible move, obey it
	  If there are two, pick the one denoted by c%2
	  If three, pick the one denoted by c%3
	  Evaluate
	  Sort the K sequences and cache the first move of the best
	  For every sequence, explore all future turns
	  Sort all of these sequences
	  Keep the K best
	  Return best first move
	*/

	class BeamEntry{
	    public MOVE firstMove;
	    public int score;
	    public Game state;
	    BeamEntry(MOVE m, int i, Game copy){
		firstMove = m;
		score = i;
		state = copy;
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

	    //Make a random sequence of turns
	    ArrayList<Integer> choices = new ArrayList<Integer>();
	    for(int j = 0; j < C; j++){
		choices.add(rnd.nextInt(6));		
	    }

	    //Take them
	    MOVE firstMove = progress(choices, copy);
	    int s = score(copy);
	    BeamEntry be = new BeamEntry(firstMove,s,copy);
	    beam.add(be);
	}

	Collections.sort(beam,new CustomComparator());
	MOVE ans = beam.get(0).firstMove;

	while (timeDue-K > System.currentTimeMillis()){

	    int min = K;
	    if (beam.size() < min) { min = beam.size(); }

	    //To hold child states
	    ArrayList<BeamEntry> children = new ArrayList<BeamEntry>();

	    //
	    for(int i = 0; i < min; i++){
		BeamEntry be = beam.get(i);
		if (be.state.wasPacManEaten()) { continue; }

		int pnode = be.state.getPacmanCurrentNodeIndex();
		MOVE lastMove = be.state.getPacmanLastMoveMade();
		MOVE[] moves = be.state.getPossibleMoves(pnode,lastMove);

		for (int j = 0; j < moves.length; j++){
		    ArrayList<Integer> choices = new ArrayList<Integer>();
		    choices.add(j);
		    Game copy = be.state.copy();
		    progress(choices,copy);
		    children.add(new BeamEntry(be.firstMove,score(copy),copy));
		}
	    }

	    Collections.sort(children,new CustomComparator());

	    beam.clear();
	    min = K;
	    if (K > children.size()) { min = children.size(); }
	    for(int i = 0; i < min; i++){
		beam.add(children.get(i));
	    }
	}

	try { return beam.get(0).firstMove; }
	catch (Exception e) {}
	return ans;
    }
}
