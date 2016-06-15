package com.metl.utils

import net.liftweb.common._
import net.liftweb.util.Helpers._

trait Synched {
	import java.util.concurrent.locks.ReentrantReadWriteLock
	private val rwl = new ReentrantReadWriteLock
  private val rl = rwl.readLock
  private val wl = rwl.writeLock
	
	def syncWrite[A](f:()=>A):A	= {
		wl.lock
        val ret = f()
        wl.unlock
		ret	
	}
	def syncRead[A](f:()=>A):A = {
		rl.lock
		val ret = f()
		rl.unlock
		ret
	}
	def syncReadUnlessPredicateThenWrite[A](pred:()=>Boolean,fr:()=>A,fw:()=>A):A = {
		rl.lock
		val ret = if (pred()){
			val rRet = fr()
			rl.unlock
			rRet
		} else {
			rl.unlock
			wl.lock
			val wRet = fw()
			wl.unlock
			wRet
		}
		ret
	}
}

class SynchronizedWriteMap[A,B](collection:scala.collection.mutable.HashMap[A,B] = scala.collection.mutable.HashMap.empty[A,B],updateOnDefault:Boolean = false,defaultFunction:Function[A,B] = (k:A) => null.asInstanceOf[B]) extends Synched{
	private var coll = collection
	private var defaultFunc:Function[A,B] = defaultFunction
	def += (kv: (A,B)):SynchronizedWriteMap[A,B] = syncWrite[SynchronizedWriteMap[A,B]](()=>{
		coll.+=(kv)
		this
	})
	def -= (k:A):SynchronizedWriteMap[A,B] = syncWrite[SynchronizedWriteMap[A,B]](()=>{
		coll.-=(k)
		this
	})
	def put(k:A,v:B):Option[B] = syncWrite[Option[B]](()=>coll.put(k,v))
	def update(k:A,v:B):Unit = syncWrite(()=>coll.update(k,v))
	def updated(k:A,v:B):SynchronizedWriteMap[A,B] = syncWrite[SynchronizedWriteMap[A,B]](()=>{
		val newColl = this.clone
		newColl.update(k,v)
		newColl
	})
	def default(k:A):B = syncWrite(()=>{
		val newValue = defaultFunc(k)
		if (updateOnDefault)
			coll += ((k,newValue))
		newValue
	})
	def remove(k:A):Option[B] = syncWrite(()=>coll.remove(k))
	def clear:Unit = syncWrite(()=>coll.clear)
	def getOrElseUpdate(k:A, default: => B):B = syncWrite(()=>coll.getOrElseUpdate(k,default))
	def transform(f: (A,B) => B):SynchronizedWriteMap[A,B] = syncWrite[SynchronizedWriteMap[A,B]](()=>{
		coll.transform(f)
		this
	})
	def retain(p: (A,B) => Boolean):SynchronizedWriteMap[A,B] = syncWrite[SynchronizedWriteMap[A,B]](()=>{
		coll.retain(p)
		this
	})
	def iterator: Iterator[(A, B)] = syncRead(()=>coll.iterator)
	def values: scala.collection.Iterable[B] = syncRead(()=>coll.map(kv => kv._2))
	def valuesIterator: Iterator[B] = syncRead(()=>coll.valuesIterator)
	def foreach[U](f:((A, B)) => U) = syncRead(()=>coll.foreach(f))
	def apply(k: A): B = syncReadUnlessPredicateThenWrite(()=>isDefinedAt(k),()=>coll.apply(k),()=>default(k))
	def withDefault(f:(A) => B):SynchronizedWriteMap[A,B] = syncWrite(()=>{
		this.defaultFunc = f
		this
	})
	def keySet: scala.collection.Set[A] = syncRead(()=>coll.keySet)
	def keys: scala.collection.Iterable[A] = syncRead(()=>coll.map(kv => kv._1)) 
	def keysIterator: Iterator[A] = syncRead(()=>coll.keysIterator)
	def isDefinedAt(k: A) = syncRead(()=>coll.isDefinedAt(k))
    def size:Int = syncRead(()=>coll.size)
    def isEmpty: Boolean = syncRead(()=>coll.isEmpty)
	def toList:List[(A,B)] = syncRead(()=>coll.toList)
	def toArray:Array[(A,B)] = syncRead(()=>coll.toArray)
	def map(f:((A, B)) => Any):scala.collection.mutable.Iterable[Any] = syncRead(()=>coll.map(f))
	override def clone: SynchronizedWriteMap[A,B] = syncRead(()=>new SynchronizedWriteMap[A,B](coll.clone,updateOnDefault,defaultFunction))
	override def toString = "SynchronizedWriteMap("+coll.map(kv => "(%s -> %s)".format(kv._1,kv._2)).mkString(", ")+")"
	override def equals(other:Any) = (other.isInstanceOf[SynchronizedWriteMap[A,B]] && other.asInstanceOf[SynchronizedWriteMap[A,B]].toList == this.toList)
}


