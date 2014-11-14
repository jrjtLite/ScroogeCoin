import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;

public class MaxFeeTxHandler {

	public static final int VALID=1;
	public static final int INVALID=-1;
	public static final int POT_VALID=0;
	
    private UTXOPool up;

	/* Creates a public ledger whose current UTXOPool (collection of unspent 
	 * transaction outputs) is utxoPool. This should make a defensive copy of 
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public MaxFeeTxHandler(UTXOPool utxoPool) {
		// IMPLEMENT THIS
	    up = new UTXOPool(utxoPool);
	}

	/* Returns true if 
	 * (1) all outputs claimed by tx are in the current UTXO pool, 
	 * (2) the signatures on each input of tx are valid, 
	 * (3) no UTXO is claimed multiple times by tx, 
	 * (4) all of tx's output values are non-negative, and
	 * (5) the sum of tx's input values is greater than or equal to the sum of   
	        its output values;
	   and false otherwise.
	 */

	public boolean isValidTx(Transaction tx) {
	    
		ArrayList<UTXO> seenUTXO = new ArrayList<UTXO>();
		
		double inSum = 0;
		double outSum = 0;
		
		int index = 0;

		for (Transaction.Input in : tx.getInputs()) {
			
			UTXO checkUTXO = new UTXO(in.prevTxHash, in.outputIndex);
			if (seenUTXO.contains(checkUTXO)) return false; // 3
			//no UTXO is claimed multiple times by tx
			
			seenUTXO.add(checkUTXO);
			
			//if the transaction pool doesn't contain it already
			if (!up.contains(checkUTXO)) return false; // 1
			
			inSum += up.getTxOutput(checkUTXO).value;
			
			// Check Signature
			if (!up.getTxOutput(checkUTXO).address.verifySignature(tx.getRawDataToSign(index), in.signature)) return false; // 2
			
			index++;
		}
		
		for (Transaction.Output out : tx.getOutputs()) {
			if (out.value < 0) return false; // 4
			outSum += out.value;
		}
		
		if (outSum > inSum) return false; // 5
		
		return true;
	}
	
	/*
	 * Similar to above, but returns either VALID, POT_VALID (if not 
	 *  all inputs are in UTXO pool but everything else checks), or
	 *  INVALID.
	 */
	public int classifyTx(Transaction tx) {
	    int result = VALID;
		ArrayList<UTXO> seenUTXO = new ArrayList<UTXO>();
		
		double inSum = 0;
		double outSum = 0;
		
		int index = 0;

		for (Transaction.Input in : tx.getInputs()) {
			
			UTXO checkUTXO = new UTXO(in.prevTxHash, in.outputIndex);
			if (seenUTXO.contains(checkUTXO)) return INVALID; // 3
			//no UTXO is claimed multiple times by tx
			
			seenUTXO.add(checkUTXO);
			
			//if the transaction pool doesn't contain it already
			if (!up.contains(checkUTXO)) {
				result = POT_VALID;
			} // 1
			
			inSum += up.getTxOutput(checkUTXO).value;
			
			// Check Signature
			if (!up.getTxOutput(checkUTXO).address.verifySignature(tx.getRawDataToSign(index), in.signature)) 
				return INVALID; // 2
			
			index++;
		}
		
		for (Transaction.Output out : tx.getOutputs()) {
			if (out.value < 0) return INVALID; // 4
			outSum += out.value;
		}
		
		if (outSum > inSum) return INVALID; // 5
		
		return result;
	}
	
	/*
	 * classifies transaction AND creates a wrapper.
	 */
	public TxWrapper wrapTx(Transaction tx) {
	    int result = VALID;
		ArrayList<UTXO> seenUTXO = new ArrayList<UTXO>();
		
		double inSum = 0;
		double outSum = 0;
		
		int index = 0;

		for (Transaction.Input in : tx.getInputs()) {
			
			UTXO checkUTXO = new UTXO(in.prevTxHash, in.outputIndex);
			if (seenUTXO.contains(checkUTXO)) return null; // 3
			//no UTXO is claimed multiple times by tx
			
			seenUTXO.add(checkUTXO);
			//if the transaction pool doesn't contain it already
			if (!up.contains(checkUTXO)) {
				result = POT_VALID;
			} // 1
			
			inSum += up.getTxOutput(checkUTXO).value;
			
			// Check Signature
			if (!up.getTxOutput(checkUTXO).address.verifySignature(tx.getRawDataToSign(index), in.signature)) 
				return null; // 2
			
			index++;
		}
		
		for (Transaction.Output out : tx.getOutputs()) {
			if (out.value < 0) return null; // 4
			outSum += out.value;
		}
		
		if (outSum > inSum) return null; // 5
		
		return new TxWrapper(new Transaction(tx), inSum - outSum, result);
	}
	
	//this only checks if all the inputs are in the UTXO pool
	public int quickCheck(Transaction tx) {
		for (Transaction.Input in : tx.getInputs()) {
			
			UTXO checkUTXO = new UTXO(in.prevTxHash, in.outputIndex);
			
			//if the transaction pool doesn't contain it already
			if (!up.contains(checkUTXO)) {
				return POT_VALID;
			} 

		}
		return VALID;
	}

	/* Handles each epoch by receiving an unordered array of proposed 
	 * transactions, checking each transaction for correctness, 
	 * returning a mutually valid array of accepted transactions, 
	 * and updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		//return basicHandleTxs(possibleTxs);
		return greedyHandleTxs(possibleTxs);
	}
	
	public Transaction[] basicHandleTxs(Transaction[] possibleTxs) {
		ArrayList<Transaction> goodTx = new ArrayList<Transaction>();
		
		boolean isDone = false;
		
		while (!isDone) {
			isDone = true;
			
			for (int i = 0; i < possibleTxs.length; i++) {
				if (possibleTxs[i] == null) continue;
				if (isValidTx(possibleTxs[i])) {
					// Remove old UTXOs from Pool
					for (Transaction.Input in : possibleTxs[i].getInputs()) {
						UTXO delUTXO = new UTXO(in.prevTxHash, in.outputIndex);
						up.removeUTXO(delUTXO);
					}
					
					// Add new UTXOs to Pool
					for(int j = 0; j < possibleTxs[i].getOutputs().size(); j++) {
						UTXO newUTXO = new UTXO(possibleTxs[i].getHash(), j);
						up.addUTXO(newUTXO, possibleTxs[i].getOutputs().get(j));
					}
					
					goodTx.add(possibleTxs[i]);
					
					// Set Array Element to Null
					possibleTxs[i] = null;
					
					// Not Done Yet!
					isDone = false;
				}
			}
		}
		
		Transaction[] tArr = new Transaction[goodTx.size()];
		tArr = goodTx.toArray(tArr);
		return tArr;
	}
	
	
	//node for graph
	
	public class TxWrapper implements Comparable<TxWrapper> {
		private Transaction tx;
		private ArrayList<TxWrapper> refs;
		private double fee;
		private int validity;
		public TxWrapper(Transaction tx, double fee, int validity) {
		    this.setTx(tx);
		    this.setFee(fee);
		    this.setValidity(validity);
		    this.refs = new ArrayList<TxWrapper>();
		}
		public Transaction getTx() {
			return tx;
		}
		public void setTx(Transaction tx) {
			this.tx = tx;
		}
		public ArrayList<TxWrapper> getRefs() {
			return refs;
		}
		public int compareTo(TxWrapper tx2) {
			return Double.compare(fee,tx2.getFee());
		}
		public double getFee() {
			return fee;
		}
		public void setFee(double fee) {
			this.fee = fee;
		}
		public int getValidity() {
			return validity;
		}
		public void setValidity(int validity) {
			this.validity = validity;
		}
		public void addRef(TxWrapper tx2) {
			refs.add(tx2);
		}
	}
	
//	public double txFee(Transaction tx) {
//		double inSum=0;
//		for (Transaction.Input in : tx.getInputs()) {
//			
//			inSum += up.getTxOutput(checkUTXO).value;
//			
//			// Check Signature
//			if (!up.getTxOutput(checkUTXO).address.verifySignature(tx.getRawDataToSign(index), in.signature)) 
//				return INVALID; // 2
//			
//			index++;
//		}
//		
//		for (Transaction.Output out : tx.getOutputs()) {
//			if (out.value < 0) return INVALID; // 4
//			outSum += out.value;
//		}
//		
//		if (outSum > inSum) return INVALID; // 5
//	}
//	
//	public class TxFeeComparator implements Comparator<Transaction> {
//
//		public int compare(Transaction t1, Transaction t2) {
//			// TODO Auto-generated method stub
//			return 0;
//		}
//		
//	}
	
	public Transaction[] greedyHandleTxs(Transaction[] possibleTxs) {
		
		//Plan
		// (1) first create a hash to transaction table for possibleTxs
		/* (2) for each transaction, if it's invalid, then kill it. 
		 *  If all inputs are in UTXOPool, add it to nbrsOfGood. 
		 *  For each input, add that transaction to the "refs" list 
		 *  of the referenced address.
		 * (3) order potGoodTxs by transaction fee (make txFee a method).
		 *  Repeat until potGoodTxs is empty: take the transaction tx with maximum fee in 
		 *  nbrsOfGood and if
		 *  it's valid, then put it in UTXOPool.
		 *  Take any transactions that attempt to double-spend the addresses just spent
		 *  and delete them from potGoodTxs (optional).
		 *   Check neighbors of tx; if they are valid put them into nbrsOfGood.
		 *   
		 */
		HashMap<byte[], TxWrapper> hashToTx = new HashMap<byte[], TxWrapper>();
		PriorityQueue<TxWrapper> nbrsOfGood= new PriorityQueue<TxWrapper>();
		ArrayList<TxWrapper> potGoodTxs = new ArrayList<TxWrapper>();
		ArrayList<Transaction> goodTxs = new ArrayList<Transaction>();
		
		for (Transaction tx : possibleTxs) {
			TxWrapper wrapped = wrapTx(tx);
			if (wrapped==null) continue;//we don't put this in the set.
			hashToTx.put(tx.getHash(), wrapped);
			
			switch (wrapped.getValidity()) {
			case VALID:
				nbrsOfGood.add(wrapped);
				break;
			case POT_VALID:
				potGoodTxs.add(wrapped);
				break;
			//case INVALID: 
			//do nothing
			}
		}
		
		for (TxWrapper wrapped : potGoodTxs) {
			for (Transaction.Input in : wrapped.getTx().getInputs()) {
				byte[] hash = in.prevTxHash;
				TxWrapper origin = hashToTx.get(hash);
				if (origin == null) {
					break;
					//can do another check to see if we can actually remove this
					// but it's not a big deal.
				}
				origin.addRef(wrapped);
			}
		}
		
		while (!nbrsOfGood.isEmpty()) {
			TxWrapper top = nbrsOfGood.poll();
			//argh, I should actually check this at (*) below
			if (quickCheck(top.getTx())!=VALID) continue;
			
			goodTxs.add(top.getTx());
			// Remove old UTXOs from Pool
			for (Transaction.Input in : top.getTx().getInputs()) {
				UTXO delUTXO = new UTXO(in.prevTxHash, in.outputIndex);
				up.removeUTXO(delUTXO);
			}
			//reuse code
			for(int j = 0; j < top.getTx().getOutputs().size(); j++) {
				UTXO newUTXO = new UTXO(top.getTx().getHash(), j);
				up.addUTXO(newUTXO, top.getTx().getOutputs().get(j));
			}
			
			//now destroy all things that were invalidated. (*)
			// skip this for now
			
			for(TxWrapper nbr: top.getRefs()) {
				if (quickCheck(nbr.getTx())==VALID) {
					nbrsOfGood.add(nbr);
				}
				//if nbr is valid
				//then add it to nbrsOfGood
			}
		}
		
		Transaction[] tArr = new Transaction[goodTxs.size()];
		tArr = goodTxs.toArray(tArr);
		return tArr;
	}
	
	public class TxHandlerState implements Comparable<TxHandlerState>{
		public TxHandler handler;
		public PriorityQueue<TxWrapper> nbrsOfGood;
		public ArrayList<TxWrapper> potGoodTxs;
		public ArrayList<Transaction> goodTxs;
		public double fees;
		public TxHandlerState(TxHandler th) {
			//to save time, TxHandler should have the UTXOPool scraped 
			//of unnecessary transactions.
			handler = th;
			nbrsOfGood = new PriorityQueue<TxWrapper>();
			potGoodTxs = new ArrayList<TxWrapper>();
			goodTxs = new ArrayList<Transaction>();
			fees = 0;
		}
		//add a copy constructor!
		
		@Override
		public int compareTo(TxHandlerState t) {
			return Double.compare(fees,t.fees);
		}
		
	}
	
	public class TxSearch extends HeuristicSearch<TxHandlerState> {

		public TxSearch(TxHandlerState e) {
			super(e);
		}

		@Override
		boolean test(TxHandlerState e) {
			return (e.nbrsOfGood.isEmpty());
		}

		@Override
		ArrayList<TxHandlerState> children(TxHandlerState e) {
			// TODO Auto-generated method stub
			return null;
			//first pass through and add all SAFE transactions
			// (do this as long as there are more transactions to add)
			// (define a method isSafe)
			
			//then take the biggest and create 2 TxHandlerStates: one with it
			//inside and one without.
			
		}
		
	}
	
	public abstract class HeuristicSearch<E extends Comparable<E>> {
		
		//protected abstract E state;
		
		abstract boolean test(E e);
		
		abstract ArrayList<E> children(E e);
		
		protected PriorityQueue<E> options;	
		
		public HeuristicSearch(E e) {
			options = new PriorityQueue<E>();
			options.add(e);
		}
		//Collections.reverseOrder
		public HeuristicSearch(E e, Comparator<E> c) {
			options = new PriorityQueue<E>(11, c);
			options.add(e);
		}
		
		//trying to minimize the heuristic
		public E heuristicMinDFS() {
			while (!options.isEmpty()) {
				E top = options.poll();
				if (test(top)) {
					return top;
				}
				options.addAll(children(top));
			}
			return null;//solution not found
		}

		//trying to maximize the heuristic
		//DO: add a time!
		E heuristicMaxDFS(int ms) {
			E best = null;
			while (!options.isEmpty()) {
				E top = options.poll();
				if (test(top)) {
					if ((best == null) || (top.compareTo(best)==1)) {
						best = top;
					}
				} else {
					options.addAll(children(top));
				}
			}
			return best;//solution not found
		}
	}
	
}
