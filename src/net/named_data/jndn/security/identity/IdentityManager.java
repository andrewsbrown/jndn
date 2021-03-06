/**
 * Copyright (C) 2014 Regents of the University of California.
 * @author: Jeff Thompson <jefft0@remap.ucla.edu>
 * @author: From code in ndn-cxx by Yingdi Yu <yingdi@cs.ucla.edu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * A copy of the GNU Lesser General Public License is in the file COPYING.
 */

package net.named_data.jndn.security.identity;

import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.named_data.jndn.Data;
import net.named_data.jndn.Interest;
import net.named_data.jndn.KeyLocator;
import net.named_data.jndn.KeyLocatorType;
import net.named_data.jndn.Name;
import net.named_data.jndn.Sha256WithRsaSignature;
import net.named_data.jndn.Signature;
import net.named_data.jndn.encoding.WireFormat;
import net.named_data.jndn.encoding.der.DerDecodingException;
import net.named_data.jndn.encoding.der.DerEncodingException;
import net.named_data.jndn.security.DigestAlgorithm;
import net.named_data.jndn.security.KeyType;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.certificate.CertificateSubjectDescription;
import net.named_data.jndn.security.certificate.IdentityCertificate;
import net.named_data.jndn.security.certificate.PublicKey;
import net.named_data.jndn.util.Blob;
import net.named_data.jndn.util.Common;
import net.named_data.jndn.util.SignedBlob;

/**
 * An IdentityManager is the interface of operations related to identity, keys,
 * and certificates.
 */
public class IdentityManager {
  /**
   * Create a new IdentityManager to use the given identity and private key
   * storage.
   * @param identityStorage An object of a subclass of IdentityStorage.
   * @param privateKeyStorage An object of a subclass of PrivateKeyStorage.
   */
  public IdentityManager
    (IdentityStorage identityStorage, PrivateKeyStorage privateKeyStorage)
  {
    identityStorage_ = identityStorage;
    privateKeyStorage_ = privateKeyStorage;
  }

  /**
   * Create a new IdentityManager to use the given IdentityStorage and
   * the default PrivateKeyStorage for your system, which is
   * OSXPrivateKeyStorage for OS X, otherwise FilePrivateKeyStorage.
   * @param identityStorage An object of a subclass of IdentityStorage.
   */
  public IdentityManager(IdentityStorage identityStorage) throws SecurityException
  {
    identityStorage_ = identityStorage;

    if (System.getProperty("os.name").equals("Mac OS X"))
      throw new SecurityException
        ("OSXPrivateKeyStorage is not implemented yet. You must create an IdentityManager with a different PrivateKeyStorage.");
    else
      privateKeyStorage_ = new FilePrivateKeyStorage();
  }

  /**
   * Create a new IdentityManager to use BasicIdentityStorage and
   * the default PrivateKeyStorage for your system, which is
   * OSXPrivateKeyStorage for OS X, otherwise FilePrivateKeyStorage.
   * @param identityStorage An object of a subclass of IdentityStorage.
   */
  public IdentityManager() throws SecurityException
  {
    identityStorage_ = new BasicIdentityStorage();

    if (System.getProperty("os.name").equals("Mac OS X"))
      throw new SecurityException
        ("OSXPrivateKeyStorage is not implemented yet. You must create an IdentityManager with a different PrivateKeyStorage.");
    else
      privateKeyStorage_ = new FilePrivateKeyStorage();
  }

  /**
   * Create an identity by creating a pair of Key-Signing-Key (KSK) for this
   * identity and a self-signed certificate of the KSK.
   * @param identityName The name of the identity.
   * @return The key name of the auto-generated KSK of the identity.
   * @throws SecurityException if the identity has already been created.
   */
  public final Name
  createIdentity(Name identityName) throws SecurityException
  {
    if (!identityStorage_.doesIdentityExist(identityName)) {
      Logger.getLogger(this.getClass().getName()).log
        (Level.INFO, "Create Identity");
      identityStorage_.addIdentity(identityName);

      Logger.getLogger(this.getClass().getName()).log
        (Level.INFO, "Create Default RSA key pair");
      Name keyName = generateRSAKeyPairAsDefault(identityName, true);

      Logger.getLogger(this.getClass().getName()).log
        (Level.INFO, "Create self-signed certificate");
      IdentityCertificate selfCert = selfSign(keyName);

      Logger.getLogger(this.getClass().getName()).log
        (Level.INFO, "Add self-signed certificate as default");

      addCertificateAsDefault(selfCert);

      return keyName;
    }
    else
      throw new SecurityException("Identity has already been created!");
  }

  /**
   * Set the default identity.  If the identityName does not exist, then clear
   * the default identity so that getDefaultIdentity() throws an exception.
   * @param identityName The default identity name.
   */
  public final void
  setDefaultIdentity(Name identityName) throws SecurityException
  {
    identityStorage_.setDefaultIdentity(identityName);
  }

  /**
   * Get the default identity.
   * @return The name of default identity.
   * @throws SecurityException if the default identity is not set.
   */
  public final Name
  getDefaultIdentity() throws SecurityException
  {
    return identityStorage_.getDefaultIdentity();
  }

  /**
   * Generate a pair of RSA keys for the specified identity.
   * @param identityName The name of the identity.
   * @param isKsk true for generating a Key-Signing-Key (KSK), false for a Data-Signing-Key (KSK).
   * @param keySize The size of the key.
   * @return The generated key name.
   */
  public final Name
  generateRSAKeyPair
    (Name identityName, boolean isKsk, int keySize) throws SecurityException
  {
    Name keyName = generateKeyPair(identityName, isKsk, KeyType.RSA, keySize);

    return keyName;
  }

  /**
   * Generate a pair of RSA keys for the specified identity and default keySize
   * 2048.
   * @param identityName The name of the identity.
   * @param isKsk true for generating a Key-Signing-Key (KSK), false for a Data-Signing-Key (KSK).
   * @return The generated key name.
   */
  public final Name
  generateRSAKeyPair(Name identityName, boolean isKsk) throws SecurityException
  {
    return generateRSAKeyPair(identityName, isKsk, 2048);
  }

  /**
   * Generate a pair of RSA keys for the specified identity for a
   * Data-Signing-Key and default keySize 2048.
   * @param identityName The name of the identity.
   * @return The generated key name.
   */
  public final Name
  generateRSAKeyPair(Name identityName) throws SecurityException
  {
    return generateRSAKeyPair(identityName, false, 2048);
  }

  /**
   * Set a key as the default key of an identity.
   * @param keyName The name of the key.
   * @param identityName the name of the identity. If empty, the
   * identity name is inferred from the keyName.
   */
  public final void
  setDefaultKeyForIdentity(Name keyName, Name identityName) throws SecurityException
  {
    identityStorage_.setDefaultKeyNameForIdentity(keyName, identityName);
  }

  /**
   * Set a key as the default key of an identity, inferred from the keyName.
   * @param keyName The name of the key.
   */
  public final void
  setDefaultKeyForIdentity(Name keyName) throws SecurityException
  {
    setDefaultKeyForIdentity(keyName, new Name());
  }

  /**
   * Get the default key for an identity.
   * @param identityName the name of the identity. If empty, the identity name
   * is inferred from the keyName.
   * @return The default key name.
   * @throws SecurityException if the default key name for the identity is not set.
   */
  public final Name
  getDefaultKeyNameForIdentity(Name identityName) throws SecurityException
  {
    return identityStorage_.getDefaultKeyNameForIdentity(identityName);
  }

  /**
   * Get the default key for an identity, inferred from the keyName.
   * @return The default key name.
   * @throws SecurityException if the default key name for the identity is not set.
   */
  public final Name
  getDefaultKeyNameForIdentity() throws SecurityException
  {
    return getDefaultKeyNameForIdentity(new Name());
  }

  /**
   * Generate a pair of RSA keys for the specified identity and set it as
   * default key for the identity.
   * @param identityName The name of the identity.
   * @param isKsk true for generating a Key-Signing-Key (KSK), false for a Data-Signing-Key (KSK).
   * @param keySize The size of the key.
   * @return The generated key name.
   */
  public final Name
  generateRSAKeyPairAsDefault
    (Name identityName, boolean isKsk, int keySize) throws SecurityException
  {
    Name keyName = generateKeyPair(identityName, isKsk, KeyType.RSA, keySize);

    identityStorage_.setDefaultKeyNameForIdentity(keyName, identityName);

    return keyName;
  }

  /**
   * Generate a pair of RSA keys for the specified identity and set it as
   * default key for the identity, using the default keySize 2048.
   * @param identityName The name of the identity.
   * @param isKsk true for generating a Key-Signing-Key (KSK), false for a Data-Signing-Key (KSK).
   * @return The generated key name.
   */
  public final Name
  generateRSAKeyPairAsDefault(Name identityName, boolean isKsk) throws SecurityException
  {
    return generateRSAKeyPairAsDefault(identityName, isKsk, 2048);
  }

  /**
   * Generate a pair of RSA keys for the specified identity and set it as
   * default key for the identity for a Data-Signing-Key and using the default
   * keySize 2048.
   * @param identityName The name of the identity.
   * @return The generated key name.
   */
  public final Name
  generateRSAKeyPairAsDefault(Name identityName) throws SecurityException
  {
    return generateRSAKeyPairAsDefault(identityName, false, 2048);
  }

  /**
   * Get the public key with the specified name.
   * @param keyName The name of the key.
   * @return The public key.
   * @throws SecurityException if the keyName is not found.
   */
  public final PublicKey
  getPublicKey(Name keyName) throws SecurityException
  {
    return PublicKey.fromDer
      (identityStorage_.getKeyType(keyName), identityStorage_.getKey(keyName));
  }

  /**
   * Create an identity certificate for a public key managed by this IdentityManager.
   * @param certificatePrefix The name of public key to be signed.
   * @param signerCertificateName The name of signing certificate.
   * @param notBefore The notBefore value in the validity field of the
   * generated certificate as milliseconds since 1970.
   * @param notAfter The notAfter value in validity field of the generated
   * certificate as milliseconds since 1970.
   * @return The name of generated identity certificate.
   */
  public final Name
  createIdentityCertificate
    (Name certificatePrefix, Name signerCertificateName, double notBefore,
     double notAfter) throws SecurityException
  {
    Name keyName = getKeyNameFromCertificatePrefix(certificatePrefix);

    Blob keyBlob = identityStorage_.getKey(keyName);
    PublicKey publicKey = PublicKey.fromDer(KeyType.RSA, keyBlob);

    IdentityCertificate certificate = createIdentityCertificate
      (certificatePrefix, publicKey,  signerCertificateName, notBefore, notAfter);

    identityStorage_.addCertificate(certificate);

    return certificate.getName();
  }

  /**
   * Create an identity certificate for a public key supplied by the caller.
   * @param certificatePrefix The name of public key to be signed.
   * @param publickey The public key to be signed.
   * @param signerCertificateName The name of signing certificate.
   * @param notBefore The notBefore value in the validity field of the generated certificate.
   * @param notAfter The notAfter vallue in validity field of the generated certificate.
   * @return The generated identity certificate.
   */
  public final IdentityCertificate
  createIdentityCertificate
    (Name certificatePrefix, PublicKey publickey, Name signerCertificateName,
     double notBefore, double notAfter)
  {
    throw new UnsupportedOperationException
      ("IdentityManager.createIdentityCertificate not implemented");
  }

  /**
   * Add a certificate into the public key identity storage.
   * @param certificate The certificate to to added.  This makes a copy of the
   * certificate.
   */
  public final void
  addCertificate(IdentityCertificate certificate) throws SecurityException
  {
    identityStorage_.addCertificate(certificate);
  }

  /**
   * Set the certificate as the default for its corresponding key.
   * @param certificate The certificate.
   */
  public final void
  setDefaultCertificateForKey
    (IdentityCertificate certificate) throws SecurityException
  {
    Name keyName = certificate.getPublicKeyName();

    if (!identityStorage_.doesKeyExist(keyName))
      throw new SecurityException("No corresponding Key record for certificate!");

    identityStorage_.setDefaultCertificateNameForKey
      (keyName, certificate.getName());
  }

  /**
   * Add a certificate into the public key identity storage and set the
   * certificate as the default for its corresponding identity.
   * @param certificate The certificate to be added.  This makes a copy of the
   * certificate.
   */
  public final void
  addCertificateAsIdentityDefault(IdentityCertificate certificate) throws SecurityException
  {
    identityStorage_.addCertificate(certificate);

    Name keyName = certificate.getPublicKeyName();

    setDefaultKeyForIdentity(keyName);

    setDefaultCertificateForKey(certificate);
  }

  /**
   * Add a certificate into the public key identity storage and set the
   * certificate as the default of its corresponding key.
   * @param certificate The certificate to be added.  This makes a copy of the
   * certificate.
   */
  public final void
  addCertificateAsDefault(IdentityCertificate certificate) throws SecurityException
  {
    identityStorage_.addCertificate(certificate);

    setDefaultCertificateForKey(certificate);
  }

  /**
   * Get a certificate with the specified name.
   * @param certificateName The name of the requested certificate.
   * @return the requested certificate which is valid.
   */
  public final IdentityCertificate
  getCertificate(Name certificateName) throws SecurityException, DerDecodingException
  {
    return identityStorage_.getCertificate(certificateName, false);
  }

  /**
   * Get a certificate even if the certificate is not valid anymore.
   * @param certificateName The name of the requested certificate.
   * @return the requested certificate.
   */
  public final IdentityCertificate
  getAnyCertificate(Name certificateName) throws SecurityException, DerDecodingException
  {
    return identityStorage_.getCertificate(certificateName, true);
  }

  /**
   * Get the default certificate name for the specified identity, which will be
   * used when signing is performed based on identity.
   * @param identityName The name of the specified identity.
   * @return The requested certificate name.
   * @throws SecurityException if the default key name for the identity is not
   * set or the default certificate name for the key name is not set.
   */
  public final Name
  getDefaultCertificateNameForIdentity(Name identityName) throws SecurityException
  {
    return identityStorage_.getDefaultCertificateNameForIdentity(identityName);
  }

  /**
   * Get the default certificate name of the default identity, which will be
   * used when signing is based on identity and the identity is not specified.
   * @return The requested certificate name.
   * @throws SecurityException if the default identity is not set or the default
   * key name for the identity is not set or the default certificate name for
   * the key name is not set.
   */
  public final Name
  getDefaultCertificateName() throws SecurityException
  {
    return identityStorage_.getDefaultCertificateNameForIdentity
      (getDefaultIdentity());
  }

  /**
   * Sign the byte array data based on the certificate name.
   * @param buffer The byte buffer to be signed.
   * @param certificateName The signing certificate name.
   * @return The generated signature.
   */
  public final Signature
  signByCertificate(ByteBuffer buffer, Name certificateName) throws SecurityException
  {
    DigestAlgorithm[] digestAlgorithm = new DigestAlgorithm[1];
    Signature signature = makeSignatureByCertificate
      (certificateName, digestAlgorithm);

    signature.setSignature(privateKeyStorage_.sign(buffer, 
      IdentityCertificate.certificateNameToPublicKeyName(certificateName),
      digestAlgorithm[0]));

    return signature;
  }

  /**
   * Sign data packet based on the certificate name.
   * Use the default WireFormat.getDefaultWireFormat().
   * @param data The Data object to sign and update its signature.
   * @param certificateName The Name identifying the certificate which
   * identifies the signing key.
   */
  public final void
  signByCertificate(Data data, Name certificateName) throws SecurityException
  {
    signByCertificate(data, certificateName, WireFormat.getDefaultWireFormat());
  }

  /**
   * Sign data packet based on the certificate name.
   * @param data The Data object to sign and update its signature.
   * @param certificateName The Name identifying the certificate which
   * identifies the signing key.
   * @param wireFormat The WireFormat for calling encodeData.
   */
  public final void
  signByCertificate
    (Data data, Name certificateName, WireFormat wireFormat) throws SecurityException
  {
    DigestAlgorithm[] digestAlgorithm = new DigestAlgorithm[1];
    Signature signature = makeSignatureByCertificate
      (certificateName, digestAlgorithm);

    data.setSignature(signature);
    // Encode once to get the signed portion.
    SignedBlob encoding = data.wireEncode(wireFormat);

    data.getSignature().setSignature
      (privateKeyStorage_.sign(encoding.signedBuf(), 
       IdentityCertificate.certificateNameToPublicKeyName(certificateName),
       digestAlgorithm[0]));

    // Encode again to include the signature.
    data.wireEncode(wireFormat);
  }

  /**
   * Append a SignatureInfo to the Interest name, sign the name components and
   * append a final name component with the signature bits.
   * @param interest The Interest object to be signed. This appends name
   * components of SignatureInfo and the signature bits.
   * @param certificateName The certificate name of the key to use for signing.
   * @param wireFormat A WireFormat object used to encode the input.
   */
  public final void
  signInterestByCertificate
    (Interest interest, Name certificateName, WireFormat wireFormat) throws SecurityException
  {
    DigestAlgorithm[] digestAlgorithm = new DigestAlgorithm[1];
    Signature signature = makeSignatureByCertificate
      (certificateName, digestAlgorithm);

    // Append the encoded SignatureInfo.
    interest.getName().append(wireFormat.encodeSignatureInfo(signature));

    // Append an empty signature so that the "signedPortion" is correct.
    interest.getName().append(new Name.Component());
    // Encode once to get the signed portion, and sign.
    SignedBlob encoding = interest.wireEncode(wireFormat);
    signature.setSignature
      (privateKeyStorage_.sign(encoding.signedBuf(),
       IdentityCertificate.certificateNameToPublicKeyName(certificateName),
       digestAlgorithm[0]));

    // Remove the empty signature and append the real one.
    interest.setName(interest.getName().getPrefix(-1).append
      (wireFormat.encodeSignatureValue(signature)));
  }

  /**
   * Generate a self-signed certificate for a public key.
   * @param keyName The name of the public key.
   * @return The generated certificate.
   */
  private IdentityCertificate
  selfSign(Name keyName) throws SecurityException
  {
    IdentityCertificate certificate = new IdentityCertificate();

    Blob keyBlob = identityStorage_.getKey(keyName);
    PublicKey publicKey = PublicKey.fromDer(KeyType.RSA, keyBlob);

    Calendar calendar = Calendar.getInstance();
    double notBefore = (double)calendar.getTimeInMillis();
    calendar.add(Calendar.YEAR, 2);
    double notAfter = (double)calendar.getTimeInMillis();

    certificate.setNotBefore(notBefore);
    certificate.setNotAfter(notAfter);

    Name certificateName = keyName.getPrefix(-1).append("KEY").append
      (keyName.get(-1)).append("ID-CERT").append
      (Name.Component.fromNumber((long)certificate.getNotBefore()));
    certificate.setName(certificateName);

    certificate.setPublicKeyInfo(publicKey);
    certificate.addSubjectDescription(new CertificateSubjectDescription
      ("2.5.4.41", keyName.toUri()));
    try {
      certificate.encode();
    } catch (DerEncodingException ex) {
      // We don't expect this to happen.
      Logger.getLogger(IdentityManager.class.getName()).log(Level.SEVERE, null, ex);
      return null;
    } catch (DerDecodingException ex) {
      // We don't expect this to happen.
      Logger.getLogger(IdentityManager.class.getName()).log(Level.SEVERE, null, ex);
      return null;
    }

    signByCertificate(certificate, certificate.getName());

    return certificate;
  }

  /**
   * Generate a key pair for the specified identity.
   * @param identityName The name of the specified identity.
   * @param isKsk true for generating a Key-Signing-Key (KSK), false for a Data-Signing-Key (KSK).
   * @param keyType The type of the key pair, e.g. KEY_TYPE_RSA.
   * @param keySize The size of the key pair.
   * @return The name of the generated key.
   */
  private Name
  generateKeyPair
    (Name identityName, boolean isKsk, KeyType keyType,
     int keySize) throws SecurityException
  {
    Logger.getLogger(this.getClass().getName()).log
        (Level.INFO, "Get new key ID");
    Name keyName = identityStorage_.getNewKeyName(identityName, isKsk);

    Logger.getLogger(this.getClass().getName()).log
        (Level.INFO, "Generate key pair in private storage");
    privateKeyStorage_.generateKeyPair(keyName, keyType, keySize);

    Logger.getLogger(this.getClass().getName()).log
        (Level.INFO, "Create a key record in public storage");
    PublicKey pubKey = privateKeyStorage_.getPublicKey(keyName);
    identityStorage_.addKey(keyName, keyType, pubKey.getKeyDer());

    return keyName;
  }

  private static Name
  getKeyNameFromCertificatePrefix(Name certificatePrefix) throws SecurityException
  {
    Name result = new Name();

    String keyString = "KEY";
    int i = 0;
    for(; i < certificatePrefix.size(); i++) {
      if (certificatePrefix.get(i).toEscapedString().equals(keyString))
        break;
    }

    if (i >= certificatePrefix.size())
      throw new SecurityException
        ("Identity Certificate Prefix does not have a KEY component");

    result.append(certificatePrefix.getSubName(0, i));
    result.append
      (certificatePrefix.getSubName(i + 1, certificatePrefix.size()-i-1));

    return result;
  }

  /**
   * Return a new Signature object based on the signature algorithm of the
   * public key with keyName (derived from certificateName).
   * @param certificateName The certificate name.
   * @param digestAlgorithm Set digestAlgorithm[0] to the signature algorithm's
   * digest algorithm, e.g. DigestAlgorithm.SHA256.
   * @return A new object of the correct subclass of Signature.
   */
  private Signature
  makeSignatureByCertificate
    (Name certificateName, DigestAlgorithm[] digestAlgorithm) throws SecurityException
  {
    Name keyName = IdentityCertificate.certificateNameToPublicKeyName
      (certificateName);
    PublicKey publicKey = privateKeyStorage_.getPublicKey(keyName);

    //For temporary usage, we support RSA + SHA256 only, but will support more.
    Sha256WithRsaSignature signature = new Sha256WithRsaSignature();
    digestAlgorithm[0] = DigestAlgorithm.SHA256;

    signature.getKeyLocator().setType(KeyLocatorType.KEYNAME);
    signature.getKeyLocator().setKeyName(certificateName.getPrefix(-1));
    signature.getPublisherPublicKeyDigest().setPublisherPublicKeyDigest
      (publicKey.getDigest());

    return signature;
  }

  private IdentityStorage identityStorage_;
  private PrivateKeyStorage privateKeyStorage_;
}
