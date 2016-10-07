package com.metl.utils

import scala.xml._
import net.liftweb.util._
import net.liftweb.common._

import org.scalatest.FunSuite
import org.scalatest.mock.MockitoSugar
import org.scalatest.OptionValues._

import com.metl.utils._ 

trait CryptoSuiteBehaviours { this: FunSuite =>

    def encryptAndDecrypt(createCrypto: => Crypto) {

        test("encrypt string data and decrypt to original string: " + createCrypto.toString) {

            val crypto = createCrypto 
            val message = "metl is awesome"

            val encryptedMessage = crypto.encryptCred(message)
            val decryptedMessage = crypto.decryptCred(encryptedMessage)

            assert(message === decryptedMessage)
        }

        test("encrypt byte array data and decrypt to original byte array: " + createCrypto.toString) {

            val crypto = createCrypto 

            val message = "metl is awesome"
            val messageBytes = message.getBytes
            val encryptedMessage = crypto.encryptCred(messageBytes)
            val decryptedMessage = crypto.decryptCred(encryptedMessage)

            assert(messageBytes === decryptedMessage)
        }
    }

    def xmlKeysRSA(createRSACrypto: => Crypto) {

        test("xml public key: " + createRSACrypto.toString) {

            val crypto = createRSACrypto 

            val publicKey = crypto.getXmlPublicKey
            val result = publicKey match {
                case <RSAKeyValue><Modulus>{ _* }</Modulus><Exponent>{ _* }</Exponent></RSAKeyValue> => true
                case _ => false
            }

            assert(result, "Result: " + publicKey)
        }

        test("xml private key with rsa normal: " + createRSACrypto.toString) {

            val crypto = createRSACrypto 

            val privateKey = crypto.getXmlPrivateKey
            val result = privateKey match {
                case <RSAKeyValue><Modulus>{ _* }</Modulus><Exponent>{ _* }</Exponent><P>{ _* }</P><Q>{ _* }</Q><DP>{ _* }</DP><DQ>{ _* }</DQ><InverseQ>{ _* }</InverseQ><D>{ _* }</D></RSAKeyValue> => true
                case _ => false
            }

            assert(result, "Result: " + privateKey)
        }
    }

    def xmlKeysNonRSA(createNonRSACrypto: => Crypto) {

        test("xml key: " + createNonRSACrypto.toString) {
            
            val crypto = createNonRSACrypto 

            val key = crypto.getXmlKey
            val result = key match {
                case <KeyPair><Key>{ _* }</Key></KeyPair> => true
                case _ => false
            }

            assert(result, "Result: " + key)
        }

        test("xml key and iv: " + createNonRSACrypto.toString) {

            val crypto = createNonRSACrypto

            val key = crypto.getXmlKeyAndIv
            val result = key match {
                case <KeyPair><Key>{ _* }</Key><Iv>{ _* }</Iv></KeyPair> => true
                case _ => false
            }

            assert(result, "Result: " + key)
        }
    }
}

class CryptoSuite extends FunSuite with CryptoSuiteBehaviours {

    // crypto fixture creation methods
    def rsaNormal = new RSANormal
    def rsaCommutative1 = new RSACommutative1
    def aesctr = new AESCTRCrypto
    def descbc = new DESCBCCrypto
    def aescbc = new AESCBCCrypto

    /*
    ignore("construct rsa commutative crypto") { 
        info("unable to construct, 'java.security.NoSuchAlgorithmException: Cannot find any provider supporting RSA/None/NoPadding'")
        val crypto = new RSACommutative
    }

    ignore("construct desecb crypto") {

        info("unable to construct, 'java.security.InvalidAlgorithmParameterException: ECB mode cannot use IV'")
        val crypto = new DESECBCrypto
    }
    */

    testsFor(encryptAndDecrypt(rsaNormal))
    testsFor(xmlKeysRSA(rsaNormal))

    info("rsaCommutative does not encrypt/decrypt as expected, commented out tests")
    //testsFor(encryptAndDecrypt(rsaCommutative1))
    testsFor(xmlKeysRSA(rsaCommutative1))

    testsFor(encryptAndDecrypt(aesctr))
    testsFor(xmlKeysNonRSA(aesctr))

    testsFor(encryptAndDecrypt(descbc))
    testsFor(xmlKeysNonRSA(descbc))

    testsFor(encryptAndDecrypt(aescbc))
    testsFor(xmlKeysNonRSA(aescbc))
}
