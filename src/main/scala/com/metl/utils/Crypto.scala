package com.metl.utils

import java.math._
import java.security._
import java.security.interfaces._
import java.security.spec._
import javax.crypto._
import javax.crypto.spec._

import net.liftweb.common._

import scala.xml._

object CryptoTester extends Logger {
	val seeds = List("hello all","8charact","16characterslong","a worm","15","a")
	def testAll(crypto:Crypto) = {
		seeds.map(s => testSymmetric(crypto,s))
		val c1 = crypto.cloneCrypto
		val c2 = crypto.cloneCrypto
		seeds.map(s => testCommutative(c1,c2,s))
	}
	def testSymmetric(c:Crypto,input:String):Boolean = {
		try {
		  debug("testSymmetric: %s, %s".format(c,input))
			val inputBytes = input.getBytes
			val s1 = c.encryptCred(inputBytes)
			val s2 = c.decryptCred(s1)
			val s3 = new String(s2).trim == input.trim
			List(s1,s2,s3).foreach(li => trace("step: %s".format(li.toString)))
			debug("orig: %s, res: %s".format(input.trim,new String(s2).trim))
			s3
		} catch {
			case e:Throwable => {
				error("step: false",e)
				false
			}
		}
	}
	def testCommutative(c1:Crypto,c2:Crypto,input:String):Boolean = {
		try {
			debug("testCommutative, %s, %s".format(c1,input))
			val inputBytes = input.getBytes
			val s1 = c1.encryptCred(inputBytes)
			val s2 = c2.encryptCred(s1)
			val s3 = c1.decryptCred(s2)
			val s4 = c2.decryptCred(s3)
			val s5 = new String(s4).trim == input.trim
			val s6 = c2.encryptCred(inputBytes)
			val s7 = c1.encryptCred(s6)
			val s8 = new String(s7).trim == new String(s2).trim
			List(s1,s2,s3,s4,s5,s6,s7,s8).foreach(li => trace("step: %s".format(li.toString)))
			debug("orig: %s, res: %s".format(input.trim,new String(s4).trim))
			s5 && s8
		} catch {
			case e:Throwable => {
				error("step: false",e)
				false
			}
		}
	}
}

//this allows for key exchange between dot.net and java
class RSANormal extends Crypto("RSA","ECB","PKCS1Padding",None,None)

//these three are the only one so far that's commutative
class RSACommutative extends Crypto("RSA","None","NoPadding",None,None)

class RSACommutative1 extends Crypto("RSA","ECB","NoPadding",None,None)
class AESCTRCrypto extends Crypto("AES","CTR","NoPadding",None,None)

//these are working tripleDes
class DESCBCCrypto extends Crypto("DESede","CBC","ISO10126Padding",None,None)
class DESECBCrypto extends Crypto("DESede","ECB","ISO10126Padding",None,None)

//this is working AES
class AESCBCCrypto extends Crypto("AES","CBC","ISO10126Padding",None,None)

class Crypto(cipherName:String = "AES",cipherMode:String = "CBC",padding:String = "ISO10126Padding",inputKey:Option[Array[Byte]] = None,inputIv:Option[Array[Byte]] = None) {
	var publicKey:Option[java.security.Key] = None
	var privateKey:Option[java.security.Key] = None
	var key:Option[Object] = None
	var iv:Option[Object] = None

	var keyFactory:Option[Object] = None
	var keyGenerator:Option[Object] = None

	val cipher = List(cipherName,cipherMode,padding).filter(i => i.length > 0).mkString("/")
	val (ecipher,dcipher) = Stopwatch.time("Crypto(%s,%s,%s)".format(cipher,inputKey,inputIv), {
		val eciph = Cipher.getInstance(cipher)
		val dciph = Cipher.getInstance(cipher)

		cipherName match {
			case "RSA" => {
				//for RSA, I'm using inputKey and inputIv as the two primes necessary to share to ensure commutative RSA behaviour
				val shouldUseInput = inputKey match {
					case Some(keyBytes) => inputIv match {	
							case Some(inputIv) => true
							case _ => false 
						}
					case _ => false
				} 
				shouldUseInput match {
					case true => {
						val p = inputKey.map(i => new BigInteger(i)).getOrElse(BigInteger.probablePrime(512,new SecureRandom))
						val q = inputIv.map(i => new BigInteger(i)).getOrElse(BigInteger.probablePrime(512,new SecureRandom))
						val n = p.multiply(q)
						val phi = (p.subtract(new BigInteger("1")).multiply(q.subtract(new BigInteger("1"))))
						val modulus = n 
						var pubKeyWorking = java.lang.Math.abs((modulus.divide(new BigInteger("100")).intValue * new java.util.Random().nextFloat).toInt)
						var pubKeyFound = false
						while (pubKeyFound == false){
							print(pubKeyWorking+",")
							if (phi.gcd(new BigInteger(pubKeyWorking.toString)).equals(BigInteger.ONE)) {
								pubKeyFound = true
							} else {
								pubKeyWorking = pubKeyWorking + 1
							}
						}
						val pubExp = new BigInteger(pubKeyWorking.toString) 
						val privExp = pubExp.modInverse(phi)
						val kgen = KeyFactory.getInstance(cipherName)
						val pubKeySpec = new RSAPublicKeySpec(modulus, pubExp)
						val privKeySpec = new RSAPrivateKeySpec(modulus, privExp)
						val pubKey = kgen.generatePublic(pubKeySpec)
						val privKey = kgen.generatePrivate(privKeySpec)
						eciph.init(Cipher.ENCRYPT_MODE, pubKey)
						dciph.init(Cipher.DECRYPT_MODE, privKey)
						publicKey = Some(pubKey)
						privateKey = Some(privKey)
						keyFactory = Some(kgen)
					}
					case false => {
						val generator = KeyPairGenerator.getInstance(cipherName)
						generator.initialize(1024)
						val keyPair = generator.generateKeyPair
						val pubKey = keyPair.getPublic
						val privKey = keyPair.getPrivate
						eciph.init(Cipher.ENCRYPT_MODE, pubKey)
						dciph.init(Cipher.DECRYPT_MODE, privKey)
						publicKey = Some(pubKey)
						privateKey = Some(privKey)
						keyGenerator = Some(generator)
					}
				}
			}
			case _ => {
				val kgen = KeyGenerator.getInstance(cipherName)
				val internalKey = cipherName match {
					case "DES" => {
						kgen.init(56)
						inputKey.map(i => new SecretKeySpec(i,cipherName)).getOrElse(kgen.generateKey)
					}
					case "AES" => {
						kgen.init(128)
						inputKey.map(i => new SecretKeySpec(i,cipherName)).getOrElse(kgen.generateKey)
					}
					case "DESede" => {
						kgen.init(168)
						inputKey.map(i => new SecretKeySpec(i,cipherName)).getOrElse(kgen.generateKey)
					}
					case _ => {
						kgen.init(128)
						inputKey.map(i => new SecretKeySpec(i,cipherName)).getOrElse(kgen.generateKey)
					}	
				}	
				val internalIv = cipherName match {
                    // List(1,2,3,4,5,6,7,8).map(a => a.asInstanceOf[Byte]).toArray.take(8)
					case "DESede" =>  new IvParameterSpec(inputIv.getOrElse(Array[Byte](1,2,3,4,5,6,7,8)))
					case _ => new IvParameterSpec(inputIv.getOrElse(kgen.generateKey.getEncoded))
				}
				eciph.init(Cipher.ENCRYPT_MODE, internalKey, internalIv)
				dciph.init(Cipher.DECRYPT_MODE, internalKey, internalIv)
				key = Some(internalKey)
				iv = Some(internalIv)
				keyGenerator = Some(kgen)
			}
		}
		(eciph,dciph)
	})
	def cloneCrypto = Stopwatch.time("Crypto.cloneCrypto",new Crypto(cipherName,cipherMode,padding,inputKey,inputIv))

    override def toString = "Crypto(%s,%s,%s)".format(cipher,inputKey,inputIv)

	val encoding = "UTF8"

	def encryptCred(plainText:String):String = {
		val b64Encoder = new sun.misc.BASE64Encoder()
		b64Encoder.encode(encryptCred(plainText.getBytes))
	}
	def encryptCred(plainText:Array[Byte]):Array[Byte] = Stopwatch.time("Crypto.encryptCred",{
	  ecipher.doFinal(plainText)
	})
	def decryptCred(encryptedText:String):String = {
		val b64Encoder = new sun.misc.BASE64Decoder()
		new String(decryptCred(b64Encoder.decodeBuffer(encryptedText)))
	}
	def decryptCred(encryptedText:Array[Byte]):Array[Byte] = Stopwatch.time("Crypto.decryptCred", {
	  dcipher.doFinal(encryptedText)
	})
	def decryptToken(username:String, encryptedText:String):String = {
		"not yet implemented"
	}
	def getXmlPublicKey:Node = Stopwatch.time("Crypto.getXmlPublicKey", {
		privateKey.map(pk => {
			val b64Encoder = new sun.misc.BASE64Encoder()
			val privKey = pk.asInstanceOf[RSAPrivateCrtKey]
			val wrap = (i:BigInteger) => b64Encoder.encode(i.toByteArray)
			val keySpec = KeyFactory.getInstance(cipherName).getKeySpec(privKey, classOf[RSAPrivateCrtKeySpec])
			val mod = wrap(keySpec.getModulus)
			val pubExp = wrap(keySpec.getPublicExponent)
			<RSAKeyValue><Modulus>{mod}</Modulus><Exponent>{pubExp}</Exponent></RSAKeyValue>
		}).getOrElse(<NoKey/>)
	})
	def getXmlPrivateKey:Node = Stopwatch.time("Crypto.getXmlPrivateKey", {
		privateKey.map(pk => {
			val b64Encoder = new sun.misc.BASE64Encoder()
			val privKey = pk.asInstanceOf[RSAPrivateCrtKey]
			val wrap = (i:BigInteger) => b64Encoder.encode(i.toByteArray)
			val keySpec = KeyFactory.getInstance(cipherName).getKeySpec(privKey, classOf[RSAPrivateCrtKeySpec])
			val mod = wrap(keySpec.getModulus)
			val pubExp = wrap(keySpec.getPublicExponent)
			val p = wrap(keySpec.getPrimeP)
			val q = wrap(keySpec.getPrimeQ)
			val dp = wrap(keySpec.getPrimeExponentP)
			val dq = wrap(keySpec.getPrimeExponentQ)
			val iq = wrap(keySpec.getCrtCoefficient)
			val d = wrap(keySpec.getPrivateExponent)
			<RSAKeyValue><Modulus>{mod}</Modulus><Exponent>{pubExp}</Exponent><P>{p}</P><Q>{q}</Q><DP>{dp}</DP><DQ>{dq}</DQ><InverseQ>{iq}</InverseQ><D>{d}</D></RSAKeyValue>
		}).getOrElse(<NoKey/>)
	})
	def getXmlKey:Node = Stopwatch.time("Crypto.getXmlKey", {
		key.map(k => {
			val internalKeyBytes = k.asInstanceOf[java.security.Key].getEncoded
			val b64Encoder = new sun.misc.BASE64Encoder()
			val internalKey = b64Encoder.encode(internalKeyBytes)
			<KeyPair><Key>{internalKey}</Key></KeyPair>
		}).getOrElse(<NoKey/>)
	})
	def getXmlKeyAndIv:Node = Stopwatch.time("Crypto.getXmlKeyAndIv", {
		key.map(k => {
			val b64Encoder = new sun.misc.BASE64Encoder()
			val internalKey = b64Encoder.encode(k.asInstanceOf[java.security.Key].getEncoded)
			val internalIv = iv.map(i => b64Encoder.encode(i.asInstanceOf[javax.crypto.spec.IvParameterSpec].getIV)).getOrElse("")
			<KeyPair><Key>{internalKey}</Key><Iv>{internalIv}</Iv></KeyPair>
		}).getOrElse(<NoKey/>)
	})
}

// The Dot.Net code (Unity Compliant) which will communicate correctly with this java encryption class

/*
				// FOR RSA  -  we'll use this for the authcate exchange, and to provide the client with an appropriate key and iv for the later behaviour
				// this keystring should be fetched from the java server-side Crypto.getXmlPublicKey        
        var keyString = "<RSAKeyValue><Modulus>AI3U/glU+EL31OVNVYs7T35BLdYZ55O9yMYHzH+W/SXEwRv08/7T3nBX72m5GLSeq56gMj3gyPhLe7MaFsndne30b9NA5WqRu6CkBayR+/3tMC86bs8P7b/2k+YmKhry6q+RCf82PDXk7cEpx4P/1CeWX4AptDV4IVNmSQk2PaBH</Modulus><Exponent>AQAB</Exponent></RSAKeyValue>"; 
        var orig = "hello";
        var inputBytes = Encoding.UTF8.GetBytes(orig);
        Debug.Log("RSA from XML");
        var rprovider = new System.Security.Cryptography.RSACryptoServiceProvider(1024);
        rprovider.FromXmlString(keyString);
        var rEncryptedBytes = rprovider.Encrypt(inputBytes, false);
        Debug.Log("enc64: " + Convert.ToBase64String(rEncryptedBytes));
        Debug.Log("RSA from XML complete");


				// FOR DSA - we'll be given the key and IV during the initial handshake, and we'll then use this to make all subsequent web requests.
        var provider = new System.Security.Cryptography.TripleDESCryptoServiceProvider();
        provider.Padding = System.Security.Cryptography.PaddingMode.ISO10126;
        provider.Mode = System.Security.Cryptography.CipherMode.CBC;
        provider.GenerateKey(); 
        provider.GenerateIV();
        var encryptor = provider.CreateEncryptor();
        var decryptor = provider.CreateDecryptor();
        var enc = encryptor.TransformFinalBlock(inputBytes,0,inputBytes.Length);
        var dec = decryptor.TransformFinalBlock(enc, 0, enc.Length);
        Debug.Log(String.Format("ORIG: {0}, ENC: {1}, DEC: {2}", orig, Encoding.UTF8.GetString(enc), Encoding.UTF8.GetString(dec)));
*/
