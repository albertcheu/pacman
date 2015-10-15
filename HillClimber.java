package pacman.entries.pacman;

import pacman.controllers.Controller;
import pacman.controllers.examples.Legacy;
import pacman.game.Constants.MOVE;
import pacman.game.Game;
import pacman.game.Constants.GHOST;

import java.util.Random;

import java.util.LinkedList;
import java.util.ArrayList;

import java.util.EnumMap;

public class HillClimber extends Controller<MOVE>{

    private Legacy lg = new Legacy();
    private int C = 20;
    private Random rnd=new Random();
    private MOVE[] allMoves = MOVE.values();

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
	//Generate random move sequence
	ArrayList<MOVE> seq = new ArrayList<MOVE>();

	for(int i = 0; i < C; i++){
	    seq.add(allMoves[rnd.nextInt(4)]);
	}

	//Loop until we run out of time
	int best = Integer.MIN_VALUE;
	while (timeDue - 20 > System.currentTimeMillis()){
	    int pos = rnd.nextInt(seq.size());
	    MOVE val = allMoves[rnd.nextInt(4)];
	    MOVE oldVal = seq.get(pos);
	    seq.set(pos,val);

	    Game state = game.copy();
	    for(int i = 0; i < seq.size(); i++){
		state.advanceGame(seq.get(i),lg.getMove(state,System.currentTimeMillis()+4));
	    }
	    int s = score(state);

	    if (s > best) { best = s; }
	    else { seq.set(pos,oldVal); }
	}

	return seq.get(0);
    }
}
