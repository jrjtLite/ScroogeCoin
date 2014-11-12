import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class TxHandler {

	public static final int VALID=1;
	public static final int INVALID=-1;
	public static final int POT_VALID=0;
	
    private UTXOPool up;

	/* Creates a public ledger whose current UTXOPool (collection of unspent 
	 * transaction outputs) is utxoPool. This should make a defensive copy of 
	 * utxoPool by using the UTXOPool(UTXOPool uPool) constructor.
	 */
	public TxHandler(UTXOPool utxoPool) {
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

	/* Handles each epoch by receiving an unordered array of proposed 
	 * transactions, checking each transaction for correctness, 
	 * returning a mutually valid array of accepted transactions, 
	 * and updating the current UTXO pool as appropriate.
	 */
	public Transaction[] handleTxs(Transaction[] possibleTxs) {
		return basicHandleTxs(possibleTxs);
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
		
		return (Transaction[])goodTx.toArray();
	}
	
	/*
	 * Improvement: once it's invalid (not potentially valid), 
	 *  don't look at it again
	 */
	public Transaction[] handleTxs2(Transaction[] possibleTxs) {
		
		ArrayList<Transaction> potGoodTxs = new ArrayList<Transaction>(Arrays.asList(possibleTxs));
		ArrayList<Transaction> goodTxs = new ArrayList<Transaction>();
		
		boolean isDone = false;
		
		while (!isDone) {
			isDone = true;
			Iterator<Transaction> i = potGoodTxs.iterator();
			while (i.hasNext()) {
				//if (possibleTxs[i] == null) continue;
				//don't need to check this
				Transaction nextTx = i.next();
				switch (classifyTx(i.next())) {
				case VALID: 
					for (Transaction.Input in : nextTx.getInputs()) {
						UTXO delUTXO = new UTXO(in.prevTxHash, in.outputIndex);
						up.removeUTXO(delUTXO);
					}
					
					// Add new UTXOs to Pool
					for(int j = 0; j < nextTx.getOutputs().size(); j++) {
						UTXO newUTXO = new UTXO(nextTx.getHash(), j);
						up.addUTXO(newUTXO, nextTx.getOutputs().get(j));
					}
					
					goodTxs.add(nextTx);
					i.remove();
					isDone=false;
					break;
				case INVALID:
					i.remove();
				//case POT_VALID:
					//don't need to do anything if potentially valid.
				}
			}
		}
		
		return (Transaction[])goodTxs.toArray();
	}
	
	//node for graph
	
	public class TxWrapper {
		private Transaction tx;
		private ArrayList<Transaction> refs;
		public TxWrapper(Transaction tx, ArrayList<Transaction> refs) {
		    this.setTx(tx);
		    this.setRefs(refs);
		}
		public Transaction getTx() {
			return tx;
		}
		public void setTx(Transaction tx) {
			this.tx = tx;
		}
		public ArrayList<Transaction> getRefs() {
			return refs;
		}
		public void setRefs(ArrayList<Transaction> refs) {
			this.refs = refs;
		}
	}
	
	public Transaction[] graphHandleTxs(Transaction[] possibleTxs) {
		
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
		 *  Now 
		 */
		
		
		ArrayList<Transaction> nbrsOfGood= new ArrayList<Transaction>();
		
		
		return null;
	}

}
