package indexingTopology.util;

import indexingTopology.exception.UnsupportedGenericException;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A B+ tree
 * Since the structures and behaviors between internal node and external node are different, 
 * so there are two different classes for each kind of node.
 * @param <TKey> the data type of the key
 */
public class BTree <TKey extends Comparable<TKey>,TValue> implements Serializable{
	private volatile BTreeNode<TKey> root;
	//  private final BytesCounter counter;
	private BytesCounter counter;
	private TimingModule tm;
	private boolean templateMode;
	private SplitCounterModule sm;
	private Semaphore sem;

//	private final ReadWriteLock lock;
//	private final Lock wLock;
//	private final Lock rLock;

	public BTree(int order, TimingModule tm, SplitCounterModule sm) {
		counter = new BytesCounter();
		this.root = new BTreeLeafNode<TKey,TValue>(order,counter);
		counter.increaseHeightCount();
		templateMode = true;
		sem = new Semaphore(1);
//		this.lock = new ReentrantReadWriteLock();
//		this.wLock = lock.writeLock();
//		this.rLock = lock.readLock();

		assert tm != null : "Timing module cannot be null";
		assert sm != null : "Split counter module cannot be null";
		this.tm = tm;
		this.sm = sm;
	}

	public BTreeNode getRoot() {
		return root;
	}

	public BTree(BTree bt) throws CloneNotSupportedException{
		this.counter = (BytesCounter) bt.counter.clone();
		this.root = (BTreeNode) bt.root.clone(bt.root);
		setTimingModule(bt.tm);
		setSplitCounterModule(bt.sm);
		templateMode = bt.templateMode;

//		this.lock = new ReentrantReadWriteLock();
//		this.wLock = lock.writeLock();
//		this.rLock = lock.readLock();
	}

	public void setRoot(BTreeNode root) {
		this.root = root;
	}

	public void setCounter(BytesCounter counter) { this.counter = counter; }

	public void setTimingModule(TimingModule tm) {
		this.tm = tm;
	}

	public void setSplitCounterModule(SplitCounterModule sm) {
		this.sm = sm;
	}

	public int getTotalBytes() {
		return counter.getBytesCount();
	}

	public int getBytesEstimateForInsert(TKey key,byte [] value) throws UnsupportedGenericException {
		if (!templateMode)
			return counter.getBytesEstimateForInsert(UtilGenerics.sizeOf(key.getClass()), value.length);
		else
			return counter.getBytesEstimateForInsertInTemplate(UtilGenerics.sizeOf(key.getClass()), value.length);
	}

	public byte[] serializeTree() {
		ByteBuffer b=ByteBuffer.allocate(getTotalBytes());
		Queue<BTreeNode<TKey>> q=new LinkedList<BTreeNode<TKey>>();
		q.add(root);
		while (!q.isEmpty()) {
			BTreeNode<TKey> curr=q.remove();
			Collection<BTreeNode<TKey>> children=curr.recursiveSerialize(b);
			if (children!=null)
				q.addAll(children);
		}

		return b.array();
	}

	/**
	 * Insert a new key and its associated value into the B+ tree.
	 * return true if
	 */
/*	public void insert(TKey key, TValue value) throws UnsupportedGenericException {
		BTreeLeafNode<TKey, TValue> leaf = null;
//		rLock.lock();
//		try {
			long start = System.nanoTime();
			leaf = this.findLeafNodeShouldContainKey(key);
			long time = System.nanoTime() - start;
			tm.putDuration(Constants.TIME_LEAF_FIND.str, time);

//		} finally {
//			rLock.unlock();
//		}
//		for (int i=0;i<1000;i++) {
//			tm.startTiming(Constants.TIME_LEAF_FIND.str);
//			leaf = this.findLeafNodeShouldContainKey(key);
//			tm.endTiming(Constants.TIME_LEAF_FIND.str);
//		}
//		wLock.lock();
//		try {
			start = System.nanoTime();
			leaf.insertKeyValue(key, value, tm);
			time = System.nanoTime() - start;
//			tm.putDuration(Constants.TIME_LEAF_INSERTION.str, time);


//		for (int i=0;i<1000;i++) {
//			tm.startTiming(Constants.TIME_LEAF_INSERTION.str);
//			leaf.insertKeyValue(key,value);
//			tm.endTiming(Constants.TIME_LEAF_INSERTION.str);
//			leaf.deleteKeyValue(key,value);
//		}
			if (templateMode && leaf.isOverflow()) {
				//		tm.putDuration(Constants.TIME_SPLIT.str, 0);
				sm.addCounter();
				//
			} else if (!leaf.isOverflow()) {
//			if (!leaf.isOverflow()) {
//				tm.putDuration(Constants.TIME_SPLIT.str, 0);

//		} else {
//        if (templateMode || !leaf.isOverflow()) {
//            tm.putDuration(Constants.TIME_SPLIT.str, 0);

			} else {
				start = System.nanoTime();
				BTreeNode<TKey> n = leaf.dealOverflow(sm, leaf);
				if (n != null) {
					this.root = n;
					time = System.nanoTime() - start;
					tm.putDuration(Constants.TIME_SPLIT.str, time);
				} else {
					System.out.println("the root is null");
				}
			}
//		} finally {
//			wLock.unlock();
//		}
	}*/

	/**
	 * insert the key and value to the B+ tree
	 * based on the mode of the tree, the function will choose the corresponding protocol
	 * @param key the index value
	 * @param value  the offset
	 * @throws UnsupportedGenericException
	 */
	public void insert(TKey key, TValue value) throws UnsupportedGenericException {
		BTreeLeafNode<TKey, TValue> leaf = null;
		if (templateMode) {
			leaf = findLeafNodeShouldContainKeyInTemplate(key);
			leaf.acquireWriteLock();
			leaf.insertKeyValueInTemplateMode(key, value);
			if (leaf.isOverflow()) {
				sm.addCounter();
			}
			leaf.releaseWriteLock();
		} else {

			ArrayList<BTreeNode.NodeLock> ancestors = new ArrayList<BTreeNode.NodeLock>();
			leaf = findLeafNodeShouldContainKeyInUpdaterWithProtocolTwo(key);
			if(leaf == null) {
				leaf = findLeafNodeShouldContainKeyInUpdaterWithProtocolOne(key, ancestors);
			}
			BTreeNode root = null;
				try {
					root = leaf.insertKeyValue(key, value);
				} catch (ArrayIndexOutOfBoundsException e) {
					System.out.println("Debug thread " + Thread.currentThread().getId());
					e.printStackTrace();
					throw e;
				} catch (NullPointerException e) {
					System.out.println("Debug thread " + Thread.currentThread().getId());
					e.printStackTrace();
					throw e;
				}
//				if (root != null && root.keys.size() != 0) {
//					System.out.println("The keys of the root is " + root.keys);
//				}
				if (root != null) {
					if (root != this.root) {
//						if (sem.hasQueuedThreads()) {
//							sem.release();
//						}
//						System.out.println(root.keys);
//						System.out.println(String.format("root %d->%d by thread %d", this.root.hashCode()%10, root.hashCode()%10, Thread.currentThread().getId()));
						this.setRoot(root);
//						this.printBtree();
//						sem.release();
//						System.out.println("insert released");
					} else {
						this.setRoot(root);
					}
				}
				leaf.releaseWriteLock();
				for (BTreeNode.NodeLock ancestor : ancestors) {
					ancestor.unlock();
				}
				ancestors.clear();
			}

/**
//			if (this.getHeight() > 1) {
			if (false) {
//				leaf = findLeafNodeShouldContainKeyInUpdaterWithProtocolTwo(key);
//				if (leaf == null) {
////					System.out.println("protocol 1");
//					ArrayList<BTreeNode> ancestors = new ArrayList<BTreeNode>();
//					leaf = findLeafNodeShouldContainKeyInUpdaterWithProtocolOne(key, ancestors);
//					BTreeNode root = leaf.insertKeyValue(key, value);
//					if (root != null && root.keys.size() != 0) {
////						System.out.println("The keys of the root is " + root.keys);
//					}
//					if (root != null) {
////						System.out.println(root.keys);
//						this.setRoot(root);
////						this.printBtree();
////						System.out.println("After deal overflow, the keys of the root is " + root.keys);
////						sem.release();
////						if (sem.hasQueuedThreads()) {
////							sem.release();
////						}
////						System.out.println("insert released");
//					} else {
//						this.setRoot(root);
//					}
//					leaf.releaseWriteLock();
//					for (BTreeNode ancestor : ancestors) {
//						ancestor.releaseWriteLock();
//					}
//					ancestors.clear();
//				} else {
////					System.out.println("protocol 2");
//					leaf.insertKeyValueWithoutOverflow(key, value);
//					leaf.releaseWriteLock();
//				}
			} else {
//				System.out.println("protocol 1");
				ArrayList<BTreeNode> ancestors = new ArrayList<BTreeNode>();
				leaf = findLeafNodeShouldContainKeyInUpdaterWithProtocolOne(key, ancestors);
				BTreeNode root = leaf.insertKeyValue(key, value);
//				if (root != null && root.keys.size() != 0) {
//					System.out.println("The keys of the root is " + root.keys);
//				}
				if (root != null) {
					if (root != this.root) {
//						if (sem.hasQueuedThreads()) {
//							sem.release();
//						}
//						System.out.println(root.keys);
						this.setRoot(root);
//						this.printBtree();
//						sem.release();
//						System.out.println("insert released");
					} else {
						this.setRoot(root);
					}
				}
				leaf.releaseWriteLock();
				for (BTreeNode ancestor : ancestors) {
					ancestor.releaseWriteLock();
				}
				ancestors.clear();
			}
 */

	}


	/**
	 * TODO what happens if same key different value
	 * Search a key value on the tree and return its associated value.
	 */
/*	public ArrayList<TValue> search(TKey key) {
		BTreeLeafNode<TKey,TValue> leaf = this.findLeafNodeShouldContainKey(key);
		int index = leaf.search(key);
		return (index == -1) ? null : leaf.getValueList(index);
	}*/

//	public ArrayList<TValue> search(TKey key) {
//		ArrayList<TValue> values = null;
//		rLock.lock();
//		try {
//			BTreeLeafNode<TKey, TValue> leaf = this.findLeafNodeShouldContainKey(key);
//		    values = leaf.searchAndGetValues(key);   //Add the method searchAndGetValues to check the lock;
//			int index = leaf.searchAndGetValues(key);
//			return (index == -1) ? null : leaf.getValueList(index);
//			values = (index == -1) ? null : leaf.getValueList(index);
//		} finally {
//			rLock.unlock();
//		}
//		return values;
//	}

//	public ArrayList<TValue> search(TKey key) {
//		ArrayList<TValue> values = null;
//		BTreeLeafNode<TKey, TValue> leaf = this.findLeafNodeShouldContainKeyInReader(key);
//		values = leaf.searchAndGetValues(key);   //Add the method searchAndGetValues to check the lock;
//		return values;
//	}

	//The method below are changed to check the paper which is about concurrency in B tree

	/**
	 * search operation for the reader
	 * @param key
	 * @return values of the corresponding key.
	 */
	public ArrayList<TValue> search(TKey key) {
		ArrayList<TValue> values = null;
		BTreeLeafNode<TKey, TValue> leaf = null;
		if (!templateMode) {
//			root.accquireReadLock();
//			System.out.println("The key of the tree is " + root.keys);
			leaf = this.findLeafNodeShouldContainKeyInReader(key);
			values = leaf.searchAndGetValues(key);
//			System.out.println("search has been completed");
			leaf.releaseReadLock();
		} else {
			leaf = this.findLeafNodeShouldContainKeyInTemplate(key);
			leaf.acquireReadLock();
			values = leaf.searchAndGetValuesInTemplate(key);
			leaf.releaseReadLock();
		}
		   //Add the method searchAndGetValues to check the lock;
//		leaf.releaseReadLock();
		return values;
	}



	//The method below are changed to check the paper which is about concurrency in B tree
	public List<TValue> searchRange(TKey leftKey, TKey rightKey) {
		assert leftKey.compareTo(rightKey) <= 0 : "leftKey provided is greater than the right key";
		BTreeLeafNode<TKey,TValue> leafLeft = this.findLeafNodeShouldContainKeyInReader(leftKey);
		List<TValue> values = leafLeft.searchRange(leftKey, rightKey);
		return values;
	}

	/**
	 * Delete a key and its associated value from the tree. TODO Fix.might have a bug.
	 */
	public void delete(TKey key) {
		BTreeLeafNode<TKey,TValue> leaf = this.findLeafNodeShouldContainKey(key);

		if (leaf.delete(key) && leaf.isUnderflow()) {
			BTreeNode<TKey> n = leaf.dealUnderflow();
			if (n != null)
				this.root = n;
		}
	}

	/**
	 * Search the leaf node which should contain the specified key
	 */
	@SuppressWarnings("unchecked")
	/*
	private BTreeLeafNode<TKey,TValue> findLeafNodeShouldContainKey(TKey key) {

		BTreeNode<TKey> node = this.root;
		while (node.getNodeType() == TreeNodeType.InnerNode) {
			node = ((BTreeInnerNode<TKey>) node).getChild(node.search(key));
		}
		return (BTreeLeafNode<TKey,TValue>)node;

	} */

	private BTreeLeafNode<TKey,TValue> findLeafNodeShouldContainKeyInTemplate(TKey key) {
		BTreeNode<TKey> currentNode = this.root;
		while (currentNode.getNodeType() == TreeNodeType.InnerNode) {
			BTreeNode<TKey> node = ((BTreeInnerNode<TKey>) currentNode).getChild(currentNode.search(key));
			currentNode = node;
		}
//		currentNode.acquireWriteLock();
		return (BTreeLeafNode<TKey,TValue>) currentNode;
	}

	/**
	 * Protocol for the reader to find the leaf node that should contain key
	 * @param key
	 * @return the leaf node should contain the key
	 */

	private BTreeLeafNode<TKey,TValue> findLeafNodeShouldContainKeyInReader(TKey key) {
//		System.out.println("Hello 1" + root.keys);
//		try {
////			sem.acquire();
////			System.out.println("read acquired");
////			System.out.println("The keys of the root is " + root.keys);
////			root.acquireReadLock();
////			System.out.println("The keys of the root is " + root.keys);
////			System.out.println("read released");
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

		BTreeNode<TKey> currentRoot;
		Lock lastLock = null;
		do {
			if(lastLock!=null)
				lastLock.unlock();
			currentRoot = root;
			currentRoot.acquireReadLock();
			lastLock = currentRoot.getrLock();
		} while(currentRoot!=root);

		BTreeNode<TKey> currentNode = this.root;
//		currentNode.acquireReadLock();
//		System.out.println(root.lock.getReadLockCount());
//		System.out.println("The height of the tree is " + this.getHeight());
		while (currentNode.getNodeType() == TreeNodeType.InnerNode) {
			BTreeNode<TKey> node = ((BTreeInnerNode<TKey>) currentNode).getChildWithSpecificIndex(key);
//			if (node == currentNode) {
//				System.out.println("yes");
//			}
//			System.out.println("no");
//			System.out.println("The number of locks of the child node is : " + node.lock.getReadLockCount());
			node.acquireReadLock();
			currentNode.releaseReadLock();
			currentNode = node;
		}
		return (BTreeLeafNode<TKey,TValue>) currentNode;

	}





	/**
	 * Protocol 2 for the updater to find the leaf node that should contain key
	 * @param key
	 * @return the leaf node that contains the key
	 */

	private BTreeLeafNode<TKey,TValue> findLeafNodeShouldContainKeyInUpdaterWithProtocolTwo(TKey key) {


		BTreeNode<TKey> currentRoot;
		Lock lastLock = null;
		do {
			if(lastLock!=null)
				lastLock.unlock();
			currentRoot = root;
			currentRoot.acquireReadLock();
			lastLock = currentRoot.getrLock();
		} while(currentRoot!=root);


		if(getHeight()==1) {
			currentRoot.releaseReadLock();
			return null;
		}

//		System.out.println("Hello 2" + root.keys);
		BTreeNode<TKey> currentNode = this.root;
		Lock currentLock = root.getrLock();
//		printBtree();
		while (currentNode.getNodeType() == TreeNodeType.InnerNode) {
			BTreeNode<TKey> son = ((BTreeInnerNode<TKey>) currentNode).getChildWithSpecificIndex(key);
			Lock lock;
			if (son.getNodeType() == TreeNodeType.InnerNode) {
				son.acquireReadLock();
				lock = son.getrLock();
			} else {
				son.acquireWriteLock();
				lock = son.getwLock();
			}
//			currentNode.releaseReadLock();
			currentLock.unlock();
			currentNode = son;
			currentLock = lock;
		}
		if (!currentNode.isSafe()) {
//			currentNode.releaseWriteLock();
			currentLock.unlock();
			return null;
		}
		return (BTreeLeafNode<TKey,TValue>) currentNode;

	}

	/**
	 * Protocol 1 for the updater to find the leaf node that should contain key
	 * @param key
	 * @param ancestorsOfCurrentNode record the ancestor of the node which needs to be split
	 * @return the leaf node
	 */

	private BTreeLeafNode<TKey,TValue> findLeafNodeShouldContainKeyInUpdaterWithProtocolOne(TKey key, List<BTreeNode.NodeLock> ancestorsOfCurrentNode) {
//		BTreeNode<TKey> currentRoot = root;
//		currentRoot.acquireWriteLock();
//		if(root != currentRoot) {
//			System.out.println("Such a thing happens on thread " + Thread.currentThread().getId());
//			currentRoot.releaseWriteLock();
//			root.acquireWriteLock();
//		}

		BTreeNode<TKey> currentRoot;
		Lock lastLock = null;
		do {
			if(lastLock!=null)
				lastLock.unlock();
			currentRoot = root;
			currentRoot.acquireWriteLock();
			lastLock = currentRoot.getwLock();
		} while(currentRoot!=root);

//		System.out.println("The btree is ");
//		this.printBtree();
		BTreeNode<TKey> currentNode = currentRoot;
		String debug = "root = " + currentRoot.getId();
		String unlocked = "";
		while (currentNode.getNodeType() == TreeNodeType.InnerNode) {
			BTreeNode<TKey> node = ((BTreeInnerNode<TKey>) currentNode).getChildWithSpecificIndex(key);
//			if(node.getParent().getId()!=currentNode.getId()) {
//				System.out.println("Inconsistency is detected!");
//			}
			ancestorsOfCurrentNode.add(new BTreeNode.NodeLock(currentNode.getwLock(), currentNode.getId()));
			debug += " visited: " + node.getId();
			node.acquireWriteLock();
//			System.out.println("Iterate: " + node.getParent().keys);
			if (node.isSafe()) {
				for (BTreeNode.NodeLock ancestor : ancestorsOfCurrentNode) {
					try {
						ancestor.unlock();
						unlocked += ancestor.getNodeId() + " ";
					} catch (IllegalMonitorStateException e ) {
						System.out.println("Error happens when w- on " + ancestor.hashCode() % 10 + " by thread " + Thread.currentThread().getId());
//						System.out.println(debug);
						System.out.println(unlocked);
						throw e;
					}
				}
				ancestorsOfCurrentNode.clear();
			}
			currentNode = node;
		}
//		System.out.println("Before Hello");
		if ((!root.isSafe() && this.getHeight() == 1) || (!root.isSafe() && ancestorsOfCurrentNode.contains(root))) {
//			try {
////				sem.acquire();
////				System.out.println("insert acquired");
////				System.out.println("CurrentNode " + currentNode.keys);
////				if (currentNode.getParent() != null) {
////					System.out.println("ParentNode " + currentNode.getParent().keys);
////				}
////				for (BTreeNode ancestor : ancestorsOfCurrentNode) {
////					System.out.println("ancestors:" + ancestor.keys);
////				}
////				this.printBtree();
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
		}
//		System.out.println("After Hello");
//		System.out.println("finished: " + debug + " thread " + Thread.currentThread().getId());
		return (BTreeLeafNode<TKey,TValue>) currentNode;

	}
	private BTreeLeafNode<TKey,TValue> findLeafNodeShouldContainKey(TKey key) {
		BTreeNode<TKey> currentNode = this.root;
		while (currentNode.getNodeType() == TreeNodeType.InnerNode) {
//			currentNode = ((BTreeInnerNode<TKey>) currentNode).getChildWithSpecificIndex(key);
//			currentNode = ((BTreeInnerNode<TKey>) currentNode).getChild(currentNode.search(key));
		}
		return (BTreeLeafNode) currentNode;
	}


	/*  method to keep tree template intact, while just removing the tree data payload
	 */
	public void clearPayload() {
		templateMode = true;
		Queue<BTreeNode<TKey>> q = new LinkedList<BTreeNode<TKey>>();
		q.add(this.root);
		while (!q.isEmpty()) {
			BTreeNode<TKey> curr = q.remove();
			if (curr.getNodeType().equals(TreeNodeType.LeafNode)) {
				((BTreeLeafNode) curr).clearNode();

			} else {
				q.addAll(((BTreeInnerNode) curr).children);
			}
		}
	}


	public void printBtree() {
		Queue<BTreeNode<TKey>> q = new LinkedList<BTreeNode<TKey>>();
		//	int height = 0;
		//	int numberOfLeaves = 0;
		//	List<TKey> list = new LinkedList<TKey>();
		q.add(root);
		while (!q.isEmpty()) {
			//	++height;
			Queue<BTreeNode<TKey>> qInner = new LinkedList<BTreeNode<TKey>>();
			//	list = new LinkedList<TKey>();
			//	numberOfLeaves = 0;
			while (!q.isEmpty()) {
				BTreeNode<TKey> curr = q.remove();
				//		++numberOfLeaves;
				if (curr.getNodeType().equals(TreeNodeType.InnerNode)) {
					qInner.addAll(((BTreeInnerNode) curr).children);
				}
				for (TKey k : curr.keys) {
					//				list.add(k);
					System.out.print(k + " ");
				}

				System.out.print(": ");
			}

			System.out.println();
			q = qInner;
			//	if (q.isEmpty()) {
			//		System.out.println("The number of leaves of the tree is " + numberOfLeaves);
			//	}
		}
		//	System.out.println("The height of BTree is " + height);
		//	return list;
	}


	public Object clone(BTree bt) throws CloneNotSupportedException{
		BTree newBtree = new BTree(bt);
		return newBtree;
	}

	public static Object deepClone(Object object) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(object);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			return ois.readObject();
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public void setHeight(int height) {
		this.counter.setHeight(height);
	}

	public int getHeight() {
		return counter.getHeightCount();
	}

	public boolean validateParanetReference() {
		return root.validateParentReference();
	}

	public boolean validateNoDuplicatedChildReference() {
		return root.validateNoDuplicatedChildReference();
	}
}

