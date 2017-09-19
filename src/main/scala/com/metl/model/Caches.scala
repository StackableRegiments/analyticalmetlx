package com.metl.model

import com.metl.data._
import com.metl.utils._
import _root_.net.liftweb.util._
import Helpers._
import _root_.net.liftweb.common._
import net.liftweb.util
import net.sf.ehcache.config.PersistenceConfiguration
import com.metl.liftAuthenticator.OrgUnit

class ConversationCache(config:ServerConfiguration,onConversationDetailsUpdated:Conversation => Unit) {
  import org.apache.lucene.analysis.standard.StandardAnalyzer
  import org.apache.lucene.document.{Document,Field,StringField,TextField,IntPoint,LongPoint}
  import org.apache.lucene.index.{DirectoryReader,IndexReader,IndexWriter,IndexWriterConfig,Term}
  import org.apache.lucene.queryparser.classic.{ParseException,QueryParser,MultiFieldQueryParser}
  import org.apache.lucene.search.{IndexSearcher,Query,ScoreDoc,TopDocs}
  import org.apache.lucene.store.{Directory,RAMDirectory}

  protected val analyzer = new StandardAnalyzer()
  protected val slideIndex:Directory = new RAMDirectory()
  protected val conversationIndex:Directory = new RAMDirectory()
  
  protected val conversationCache:scala.collection.mutable.Map[String,Conversation] = scala.collection.mutable.Map()
  protected val slideCache:scala.collection.mutable.Map[String,Slide] = scala.collection.mutable.Map()

  protected def conversationDocFromConversation(c:Conversation):Document = {
    val doc = new Document()
    doc.add(new StringField("jid",c.jid,Field.Store.YES))
    doc.add(new TextField("title",c.title,Field.Store.YES))
    doc.add(new StringField("author",c.author,Field.Store.YES))
    doc.add(new StringField("subject",c.subject,Field.Store.YES))
    doc.add(new StringField("tag",c.tag,Field.Store.YES))
    doc.add(new LongPoint("created",c.created))
    doc.add(new LongPoint("lastModified",c.lastAccessed))
    doc.add(new TextField("slides",c.slides.map(_.id).mkString(" "),Field.Store.YES))
    doc
  }
  protected def slideDocFromSlide(s:Slide):Document = {
    val doc = new Document()
    doc.add(new StringField("id",s.id,Field.Store.YES))
    doc.add(new StringField("author",s.author,Field.Store.YES))
    doc.add(new IntPoint("index",s.index))
    doc.add(new StringField("type",s.slideType,Field.Store.YES))
    doc
  }
  protected def updateLuceneConversationCache(jid:String,c:Conversation,writer:Option[IndexWriter] = None) = {
    val w = writer.getOrElse({
      val indexConfig = new IndexWriterConfig(analyzer)
      new IndexWriter(conversationIndex,indexConfig)
    })
    w.updateDocument(new Term("jid",c.jid),conversationDocFromConversation(c).getFields())
    writer.getOrElse(w.close)
  }
  protected def updateLuceneSlideCache(id:String,s:Slide,writer:Option[IndexWriter] = None) = {
    val w = writer.getOrElse({
      val indexConfig = new IndexWriterConfig(analyzer)
      new IndexWriter(slideIndex,indexConfig)
    })
    w.updateDocument(new Term("id",s.id),slideDocFromSlide(s).getFields())
    writer.getOrElse(w.close)
  }

  def startup:Unit = {
    config.getAllConversations.foreach(c => {
      conversationCache.update(c.jid,c)
    })
    val cIndexConfig = new IndexWriterConfig(analyzer)
    val cw = new IndexWriter(conversationIndex,cIndexConfig)
    config.getAllConversations.foreach(c => updateLuceneConversationCache(c.jid,c,Some(cw)))
    cw.close
    config.getAllSlides.foreach(s => {
      slideCache.update(s.id,s)
    })
    val sIndexConfig = new IndexWriterConfig(analyzer)
    val sw = new IndexWriter(slideIndex,sIndexConfig)
    config.getAllSlides.foreach(s => updateLuceneSlideCache(s.id,s,Some(sw)))
    sw.close
  }
  def shutdown:Unit = {

  }

  def getAllConversations:List[Conversation] = conversationCache.values.toList
  def getAllSlides:List[Slide] = slideCache.values.toList
  def getConversationsForSlideId(jid:String):List[String] = getAllConversations.flatMap(c => c.slides.find(_.id == jid).map(s => c.jid))
  protected val searchableConversationFields = Array("title","jid","author","subject","tag","slides")
  def searchForConversation(query:String):List[Conversation] = {
    val q:Query = new MultiFieldQueryParser(searchableConversationFields,analyzer).parse(query)
    println("query [%s] => %s".format(query,q))
    val reader = DirectoryReader.open(conversationIndex)
    val searcher = new IndexSearcher(reader)
    val topDocs:TopDocs = searcher.search(q,conversationCache.size)
    val hits:Array[ScoreDoc] = topDocs.scoreDocs
    val results = hits.toList.flatMap(h => {
      val d:Document = searcher.doc(h.doc)
      val jid:String = d.get("jid")
      val res = conversationCache.get(jid)
      println("found: [%s] %s => %s\r\n%s\r\n%s".format(jid,h.doc,searcher.explain(q,h.doc),d,res))
      res
    })
    results
    //getAllConversations.filter(c => c.title.toLowerCase.trim.contains(query.toLowerCase.trim) || c.author.toLowerCase.trim == query.toLowerCase.trim).toList
  }
  def shouldDisplayConversation(c:Conversation,includeDeleted:Boolean = false,user:String = Globals.currentUser.is,groups:List[OrgUnit] = Globals.getUserGroups):Boolean = {
    com.metl.snippet.Metl.shouldDisplayConversation(c,includeDeleted,user,groups)
  }
  def shouldModifyConversation(user:String,c:Conversation):Boolean = {
    com.metl.snippet.Metl.shouldModifyConversation(user,c)
  }
  def queryAppliesToConversation(query:String,c:Conversation):Boolean = {
    val q:Query = new MultiFieldQueryParser(searchableConversationFields,analyzer).parse(query)
    val doc:Document = conversationDocFromConversation(c)
    true // still working out how to apply the query to filter a single conversation without spinning up a fresh lucene, which seems dumb
  }
  def searchForConversationByCourse(courseId:String):List[Conversation] = getAllConversations.filter(c => c.subject.toLowerCase.trim.equals(courseId.toLowerCase.trim) || c.foreignRelationship.exists(_.key.toLowerCase.trim == courseId.toLowerCase.trim)).toList
  def detailsOfConversation(jid:String):Conversation = conversationCache.get(jid).getOrElse(Conversation.empty)
  def detailsOfSlide(jid:String):Slide = slideCache.get(jid).getOrElse(Slide.empty)
  protected def updateConversation(c:Conversation):Conversation = {
    conversationCache.update(c.jid,c)
    updateLuceneConversationCache(c.jid,c)
    c.slides.foreach(s => {
      slideCache.update(s.id,s)
      updateLuceneSlideCache(s.id,s)
    })
    onConversationDetailsUpdated(c)
    c
  }
  def createConversation(title:String,author:String):Conversation = {
    updateConversation(config.createConversation(title,author))
  }
  def createSlide(author:String,slideType:String = "SLIDE",grouping:List[com.metl.data.GroupSet] = Nil):Slide = {
    val s = config.createSlide(author,slideType,grouping)
    slideCache.update(s.id,s)
    s
  }
  def deleteConversation(jid:String):Conversation = updateConversation(config.deleteConversation(jid))
  def renameConversation(jid:String,newTitle:String):Conversation = updateConversation(config.renameConversation(jid,newTitle))
  def changePermissions(jid:String,newPermissions:Permissions):Conversation = updateConversation(config.changePermissions(jid,newPermissions))
  def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = updateConversation(config.updateSubjectOfConversation(jid,newSubject))
  def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation = updateConversation(config.addSlideAtIndexOfConversation(jid,index,slideType))
  def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = updateConversation(config.addGroupSlideAtIndexOfConversation(jid,index,grouping))
  def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = updateConversation(config.reorderSlidesOfConversation(jid,newSlides))
  def updateConversation(jid:String,conversation:Conversation):Conversation = updateConversation(config.updateConversation(jid,conversation))
  def getConversationsByAuthor(author:String):List[Conversation] = getAllConversations.filter(_.author == author)
}

class ThemeCache(config:ServerConfiguration,cacheConfig:CacheConfig) {
  protected val themesByAuthorCache = new ManagedCache[String,List[Theme]]("themesByAuthor",(author) => config.getThemesByAuthor(author),cacheConfig)
  protected val authorsByThemeCache = new ManagedCache[String,List[String]]("authorByTheme",(theme) => config.getAuthorsByTheme(theme),cacheConfig)
  protected val locationsByThemeCache = new ManagedCache[String,List[String]]("locationsByTheme",(theme) => config.getSlidesByThemeKeyword(theme) ::: config.getConversationsByTheme(theme),cacheConfig)
  protected val attendancesByAuthorCache = new ManagedCache[String,List[Attendance]]("attendancesByAuthor",(author) => config.getAttendancesByAuthor(author),cacheConfig)
  def startup:Unit = {
    themesByAuthorCache.startup
    authorsByThemeCache.startup
    locationsByThemeCache.startup
  }
  def shutdown:Unit = {
    themesByAuthorCache.shutdown
    authorsByThemeCache.shutdown
    locationsByThemeCache.shutdown
  }

  def getThemesByAuthor(author:String):List[Theme] = themesByAuthorCache.get(author).getOrElse(Nil)
  def getAuthorsByTheme(theme:String):List[String] =  authorsByThemeCache.get(theme).getOrElse(Nil)
  def getSlidesByThemeKeyword(theme:String):List[String] = locationsByThemeCache.get(theme).getOrElse(Nil)
  def getConversationsByTheme(theme:String):List[String] = locationsByThemeCache.get(theme).getOrElse(Nil)
  def getAttendancesByAuthor(author:String):List[Attendance] = attendancesByAuthorCache.get(author).getOrElse(Nil)

  def addAttendance(a:Attendance):Unit = {
    if (attendancesByAuthorCache.contains(a.author)){
      attendancesByAuthorCache.update(a.author,a :: attendancesByAuthorCache.get(a.author).getOrElse(Nil))
    }
  }
  def addTheme(t:MeTLTheme):Unit = {
    if (themesByAuthorCache.contains(t.author)){
      themesByAuthorCache.update(t.author,t.theme :: themesByAuthorCache.get(t.author).getOrElse(Nil))
    }
    if (authorsByThemeCache.contains(t.theme.text)){
      authorsByThemeCache.update(t.theme.text,(t.author :: authorsByThemeCache.get(t.theme.text).getOrElse(Nil)).distinct)
    }
    if (locationsByThemeCache.contains(t.theme.text)){
      locationsByThemeCache.update(t.theme.text,(t.location :: locationsByThemeCache.get(t.theme.text).getOrElse(Nil)).distinct)
    }
  }
}

class SessionCache(config:ServerConfiguration,cacheConfig:CacheConfig){
  protected val sessionCache = new ManagedCache[String,List[SessionRecord]]("sessionsByProfileId",(pid) => config.getSessionsForProfile(pid),cacheConfig)
  def startup:Unit = {
    sessionCache.startup
  }
  def shutdown:Unit = {
    sessionCache.shutdown
  }
  def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = {
    getCurrentSessions.filter(sr => sr.accountName == accountName && sr.accountProvider == accountProvider)
  }
  def getSessionsForProfile(profileId:String):List[SessionRecord] = {
    sessionCache.get(profileId).getOrElse(Nil)
  }
  def updateSession(sessionRecord:SessionRecord):SessionRecord = {
    val updated = config.updateSession(sessionRecord)
    sessionCache.update(sessionRecord.profileId,sessionCache.get(sessionRecord.profileId).getOrElse(Nil) ::: List(updated))
    updated
  }
  def getCurrentSessions:List[SessionRecord] = {
    Nil
    //sessionCache.getAll(sessionCache.keys).toList.flatMap(_._2)
  }
}
class ResourceCache(config:ServerConfiguration,cacheConfig:CacheConfig) {
  protected val imageCache = new ManagedCache[Tuple2[Option[String],String],MeTLImage]("imageByIdentityAndJid",(ji) => {
    ji._1.map(jid => config.getImage(jid,ji._2)).getOrElse(config.getImage(ji._2))
  },cacheConfig)
  protected val resourceCache = new ManagedCache[Tuple2[Option[String],String],Array[Byte]]("resourceByIdentityAndJid",(ji) => {
    ji._1.map(jid => config.getResource(jid,ji._2)).getOrElse(config.getResource(ji._2))
  },cacheConfig)

  def startup:Unit = {
    imageCache.startup
    resourceCache.startup
  }
  def shutdown:Unit = {
    imageCache.shutdown
    resourceCache.shutdown
  }
  def getImage(jid:String,identity:String):MeTLImage = {
    imageCache.get((Some(jid),identity)).getOrElse(MeTLImage.empty)
  }
  def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = {
    val res = config.postResource(jid,userProposedId,data)
    resourceCache.update((Some(jid),res),data)
    res
  }
  def getResource(jid:String,identifier:String):Array[Byte] = {
    resourceCache.get((Some(jid),identifier)).getOrElse(Array.empty[Byte])
  }
  def insertResource(jid:String,data:Array[Byte]):String = {
    val res = config.insertResource(jid,data)
    resourceCache.update((Some(jid),res),data)
    res
  }
  def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = {
    val res = config.upsertResource(jid,identifier,data)
    resourceCache.update((Some(jid),res),data)
    res
  }
  def getImage(identity:String):MeTLImage = {
    imageCache.get((None,identity)).getOrElse(MeTLImage.empty)
  }
  def getResource(identifier:String):Array[Byte] = {
    resourceCache.get((None,identifier)).getOrElse(Array.empty[Byte])
  }
  def insertResource(data:Array[Byte]):String = {
    val res = config.insertResource(data)
    resourceCache.update((None,res),data)
    res
  }
  def upsertResource(identifier:String,data:Array[Byte]):String = {
    val res = config.upsertResource(identifier,data)
    resourceCache.update((None,res),data)
    res
  }
}
class ProfileCache(config:ServerConfiguration,cacheConfig:CacheConfig) {
  protected val profileStore = new ManagedCache[String,Profile]("profilesById",(id) => config.getProfiles(id).headOption.getOrElse(Profile.empty),cacheConfig)
  protected val accountStore = new ManagedCache[Tuple2[String,String],Tuple2[List[String],String]]("profilesByAccount",(acc) => config.getProfileIds(acc._1,acc._2),cacheConfig)
  
  def startup:Unit = {
    profileStore.startup
    accountStore.startup
  }
  def shutdown:Unit = {
    profileStore.shutdown
    accountStore.shutdown
  }
  def getProfiles(ids:String *):List[Profile] = {
    ids.flatMap(id => profileStore.get(id)).toList
    /*
    val id = nextFuncName
    println("%s called getProfiles: %s".format(id,ids))
    val (cachedKeys,uncachedKeys) = ids.toList.partition(i => profileStore.contains(i))
    val uncached = config.getProfiles(uncachedKeys:_*)
    val cached = ids.map(i => profileStore.get(i))//All(cachedKeys)
    //profileStore.updateAll(Map(uncached.map(uc => (uc.id,uc)):_*))
    uncached.foreach(uc => profileStore.update(uc.id,uc))
    val result = uncached ::: cached.toList.flatten//map(_._2)
    println("%s completed getProfiles: %s => %s".format(id,ids,result.length))
    result
    */
  }
  def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = {
    val newP = config.createProfile(name,attrs,audiences)
    profileStore.update(newP.id,newP)
    newP
  }
  def updateProfile(id:String,profile:Profile):Profile = {
    val uP = config.updateProfile(id,profile)
    profileStore.update(uP.id,uP)
    uP
  }
  def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = {
    val nar = config.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)
    val id = (accountName,accountProvider)
    val current = accountStore.get(id).getOrElse((Nil,""))
    val currentList = current._1
    val currentDefault = current._2
    val updatedValue = {
      (disabled,default) match {
        case (true,_) if profileId == currentDefault => (currentList.filterNot(_ == profileId),"")
        case (true,_) => (currentList.filterNot(_ == profileId),currentDefault)
        case (_,true) => ((profileId :: currentList).distinct,profileId)
        case (_,false) => ((profileId :: currentList).distinct,currentDefault)
      }
    }
    accountStore.update(id,updatedValue)
  }
  def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = {
    accountStore.get((accountName,accountProvider)).getOrElse((Nil,""))
  }
}

class CachingServerAdaptor(
  config:ServerConfiguration,
  themeCacheConfig:Option[CacheConfig] = None,
  profileCacheConfig:Option[CacheConfig] = None,
  resourceCacheConfig:Option[CacheConfig] = None,
  sessionCacheConfig:Option[CacheConfig] = None
) extends PassThroughAdaptor(config) {
  protected val conversationCache = Some(new ConversationCache(config,config.onConversationDetailsUpdated))
  protected val themeCache = themeCacheConfig.map(c => new ThemeCache(config,c))
  protected val profileCache = profileCacheConfig.map(c => new ProfileCache(config,c))
  protected val resourceCache = resourceCacheConfig.map(c => new ResourceCache(config,c))
  protected val sessionCache = sessionCacheConfig.map(c => new SessionCache(config,c))
 
  override val messageBusProvider = new TappingMessageBusProvider(config.messageBusProvider,(sTup:Tuple2[MeTLStanza,String]) => {
    val (s,l) = sTup
    println("message to %s going up!: %s".format(l,s))
  },
  (sTup:Tuple2[MeTLStanza,String]) => {
    val (s,l) = sTup
    println("message to %s going down!: %s".format(l,s))
    s match {
      case a:Attendance => themeCache.foreach(tc => tc.addAttendance(a))
      case t:MeTLTheme => themeCache.foreach(tc => tc.addTheme(t))
      case _ => {}
    }
  })

  override def getAllConversations = conversationCache.map(_.getAllConversations).getOrElse(config.getAllConversations)
  override def getAllSlides = conversationCache.map(_.getAllSlides).getOrElse(config.getAllSlides)
  override def getConversationsForSlideId(jid:String) = conversationCache.map(_.getConversationsForSlideId(jid)).getOrElse(config.getConversationsForSlideId(jid))
  override def searchForConversation(query:String) = conversationCache.map(_.searchForConversation(query)).getOrElse(config.searchForConversation(query))
  override def searchForConversationByCourse(courseId:String) = conversationCache.map(_.searchForConversationByCourse(courseId)).getOrElse(config.searchForConversationByCourse(courseId))
  override def detailsOfConversation(jid:String) = conversationCache.map(_.detailsOfConversation(jid)).getOrElse(config.detailsOfConversation(jid))
  override def detailsOfSlide(jid:String) = conversationCache.map(_.detailsOfSlide(jid)).getOrElse(config.detailsOfSlide(jid))
  override def createConversation(title:String,author:String) = conversationCache.map(_.createConversation(title,author)).getOrElse(config.createConversation(title,author))
  override def createSlide(author:String,slideType:String = "SLIDE",grouping:List[com.metl.data.GroupSet] = Nil):Slide = conversationCache.map(_.createSlide(author,slideType,grouping)).getOrElse(config.createSlide(author,slideType,grouping))
  override def deleteConversation(jid:String):Conversation = conversationCache.map(_.deleteConversation(jid)).getOrElse(config.deleteConversation(jid))
  override def renameConversation(jid:String,newTitle:String):Conversation = conversationCache.map(_.renameConversation(jid,newTitle)).getOrElse(config.renameConversation(jid,newTitle))
  override def changePermissions(jid:String,newPermissions:Permissions):Conversation = conversationCache.map(_.changePermissions(jid,newPermissions)).getOrElse(config.changePermissions(jid,newPermissions))
  override def updateSubjectOfConversation(jid:String,newSubject:String):Conversation = conversationCache.map(_.updateSubjectOfConversation(jid,newSubject)).getOrElse(config.updateSubjectOfConversation(jid,newSubject))
  override def addSlideAtIndexOfConversation(jid:String,index:Int,slideType:String):Conversation = conversationCache.map(_.addSlideAtIndexOfConversation(jid,index,slideType)).getOrElse(config.addSlideAtIndexOfConversation(jid,index,slideType))
  override def addGroupSlideAtIndexOfConversation(jid:String,index:Int,grouping:com.metl.data.GroupSet):Conversation = conversationCache.map(_.addGroupSlideAtIndexOfConversation(jid,index,grouping)).getOrElse(config.addGroupSlideAtIndexOfConversation(jid,index,grouping))
  override def reorderSlidesOfConversation(jid:String,newSlides:List[Slide]):Conversation = conversationCache.map(_.reorderSlidesOfConversation(jid,newSlides)).getOrElse(config.reorderSlidesOfConversation(jid,newSlides))
  override def updateConversation(jid:String,conversation:Conversation):Conversation = conversationCache.map(_.updateConversation(jid,conversation)).getOrElse(config.updateConversation(jid,conversation))
  override def getConversationsByAuthor(author:String):List[Conversation] = conversationCache.map(_.getConversationsByAuthor(author)).getOrElse(config.getConversationsByAuthor(author))
  override def getImage(identity:String) = resourceCache.map(_.getImage(identity)).getOrElse(config.getImage(identity))
  override def getImage(jid:String,identity:String) = resourceCache.map(_.getImage(jid,identity)).getOrElse(config.getImage(jid,identity))
  override def getResource(url:String) = resourceCache.map(_.getResource(url)).getOrElse(config.getResource(url))
  override def postResource(jid:String,userProposedId:String,data:Array[Byte]):String = resourceCache.map(_.postResource(jid,userProposedId,data)).getOrElse(config.postResource(jid,userProposedId,data))
  override def getResource(jid:String,identifier:String):Array[Byte] = resourceCache.map(_.getResource(jid,identifier)).getOrElse(config.getResource(jid,identifier))
  override def insertResource(jid:String,data:Array[Byte]):String = resourceCache.map(_.insertResource(jid,data)).getOrElse(config.insertResource(jid,data))
  override def upsertResource(jid:String,identifier:String,data:Array[Byte]):String = resourceCache.map(_.upsertResource(jid,identifier,data)).getOrElse(config.upsertResource(jid,identifier,data)) 
  override def createProfile(name:String,attrs:Map[String,String],audiences:List[Audience] = Nil):Profile = profileCache.map(_.createProfile(name,attrs,audiences)).getOrElse(config.createProfile(name,attrs,audiences))
  override def getProfiles(ids:String *):List[Profile] = profileCache.map(_.getProfiles(ids.toList:_*)).getOrElse(config.getProfiles(ids.toList:_*))
  override def updateProfile(id:String,profile:Profile):Profile = profileCache.map(_.updateProfile(id,profile)).getOrElse(config.updateProfile(id,profile))
  override def getProfileIds(accountName:String,accountProvider:String):Tuple2[List[String],String] = profileCache.map(_.getProfileIds(accountName,accountProvider)).getOrElse(config.getProfileIds(accountName,accountProvider))
  override def updateAccountRelationship(accountName:String,accountProvider:String,profileId:String,disabled:Boolean = false, default:Boolean = false):Unit = profileCache.map(_.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default)).getOrElse(config.updateAccountRelationship(accountName,accountProvider,profileId,disabled,default))
  override def getSessionsForAccount(accountName:String,accountProvider:String):List[SessionRecord] = sessionCache.map(_.getSessionsForAccount(accountName,accountProvider)).getOrElse(config.getSessionsForAccount(accountName,accountProvider))
  override def getSessionsForProfile(profileId:String):List[SessionRecord] = sessionCache.map(_.getSessionsForProfile(profileId)).getOrElse(config.getSessionsForProfile(profileId))
  override def updateSession(sessionRecord:SessionRecord):SessionRecord = sessionCache.map(_.updateSession(sessionRecord)).getOrElse(config.updateSession(sessionRecord))
  override def getCurrentSessions:List[SessionRecord] = sessionCache.map(_.getCurrentSessions).getOrElse(config.getCurrentSessions)
  override def getThemesByAuthor(author:String):List[Theme] = themeCache.map(_.getThemesByAuthor(author)).getOrElse(config.getThemesByAuthor(author))
  override def getSlidesByThemeKeyword(theme:String):List[String] = themeCache.map(_.getSlidesByThemeKeyword(theme)).getOrElse(config.getSlidesByThemeKeyword(theme))
  override def getConversationsByTheme(theme:String):List[String] = themeCache.map(_.getConversationsByTheme(theme)).getOrElse(config.getConversationsByTheme(theme))
  override def getAttendancesByAuthor(author:String):List[Attendance] = themeCache.map(_.getAttendancesByAuthor(author)).getOrElse(config.getAttendancesByAuthor(author))
  override def getAuthorsByTheme(theme:String):List[String] = themeCache.map(_.getAuthorsByTheme(theme)).getOrElse(config.getAuthorsByTheme(theme))

  override def shutdown:Unit = {
    super.shutdown
    resourceCache.foreach(_.shutdown)
    profileCache.foreach(_.shutdown)
    sessionCache.foreach(_.shutdown)
    conversationCache.foreach(_.shutdown)
    themeCache.foreach(_.shutdown)
  }
  protected lazy val initialize = {
    resourceCache.foreach(_.startup)
    profileCache.foreach(_.startup)
    sessionCache.foreach(_.startup)
    conversationCache.foreach(_.startup)
    themeCache.foreach(_.startup)
  }
  override def isReady:Boolean = {
    val ready = super.isReady
    initialize
    ready
  }

}

case class CacheConfig(heapSize:Int,heapUnits:net.sf.ehcache.config.MemoryUnit,memoryEvictionPolicy:net.sf.ehcache.store.MemoryStoreEvictionPolicy,timeToLiveSeconds:Option[Int]=None)

class ManagedCache[A <: Object,B <: Object](name:String,creationFunc:A=>B,cacheConfig:CacheConfig) extends Logger {
  import net.sf.ehcache.{Cache,CacheManager,Element,Status,Ehcache}
  import net.sf.ehcache.loader.{CacheLoader}
  import net.sf.ehcache.config.{CacheConfiguration,MemoryUnit,SizeOfPolicyConfiguration}
  import net.sf.ehcache.store.{MemoryStoreEvictionPolicy}
  import java.util.Collection
  import scala.collection.JavaConversions._
  protected val cm = CacheManager.getInstance()
  val cacheName = "%s_%s".format(name,nextFuncName)
  val sizeOfPolicy:SizeOfPolicyConfiguration = {
    val p = new SizeOfPolicyConfiguration()
    p.setMaxDepth(1024 * 1024 * 1024)
    val c = SizeOfPolicyConfiguration.MaxDepthExceededBehavior.CONTINUE
    p.setMaxDepthExceededBehavior(c.name)
    p
  }
  val cacheConfiguration = new CacheConfiguration()
    .name(cacheName)
    .maxBytesLocalHeap(cacheConfig.heapSize,cacheConfig.heapUnits)
    .eternal(false)
    .memoryStoreEvictionPolicy(cacheConfig.memoryEvictionPolicy)
    .persistence(new PersistenceConfiguration().strategy(PersistenceConfiguration.Strategy.NONE))
    .logging(false)
    .copyOnRead(false)
    .copyOnWrite(false)
//    .sizeOfPolicy(sizeOfPolicy)
  cacheConfig.timeToLiveSeconds.foreach(s => cacheConfiguration.timeToLiveSeconds(s))
  val cache = new Cache(cacheConfiguration)
  cm.addCache(cache)
  class FuncCacheLoader extends CacheLoader {
    override def clone(cache:Ehcache):CacheLoader = new FuncCacheLoader 
    def dispose:Unit = {}
    def getName:String = getClass.getSimpleName
    def getStatus:Status = cache.getStatus
    def init:Unit = {}
    def load(key:Object):Object = {
      if (key == null || key == ""){
        throw new Exception("cache %s loading passed empty or null key")
        null
      } else {
        trace("cache %s loading (%s)".format(name,key))
        key match {
          case k:A => {
            creationFunc(k).asInstanceOf[Object]
          }
          case other => {
            trace("cache %s loading (%s) supplied other type of key [%s]".format(name,key,other))
            null
          }
        }
      }
    }
    def load(key:Object,arg:Object):Object = load(key) // not yet sure what to do with this argument in this case
    def loadAll(keys:Collection[_]):java.util.Map[Object,Object] = Map(keys.toArray.toList.map(k => (k,load(k))):_*)
    def loadAll(keys:Collection[_],argument:Object):java.util.Map[Object,Object] = Map(keys.toArray.toList.map(k => (k,load(k,argument))):_*)
  }
  val loader = new FuncCacheLoader
  def keys:List[A] = cache.getKeys.toList.map(_.asInstanceOf[A])
  def contains(key:A):Boolean = key != null && cache.isKeyInCache(key)
  def get(key:A):Option[B] = {
    cache.getWithLoader(key,loader,null) match {
      case null => {
        warn("getWithLoader(%s) returned null".format(key))
        None
      }
      case e:Element => e.getObjectValue match {
        case i:B => {
          trace("getWithLoader(%s) returned %s".format(key,i))
          Some(i)
        }
        case other => {
          trace("getWithLoader(%s) returned %s cast to type".format(key,other))
          Some(other.asInstanceOf[B])
        }
      }
    }
  }
  def update(key:A,value:B):Unit = {
    cache.put(new Element(key,value))
    warn("put keys in cache[%s]: %s (%s)".format(name,keys.length,cache.getSize))
  }
  def startup = try {
    if (cache.getStatus == Status.STATUS_UNINITIALISED){
      cache.initialise
    }
  } catch {
    case e:Exception => {
      error("exception initializing ehcache: %s".format(e.getMessage),e)
    }
  }
  def shutdown = cache.dispose()
}

