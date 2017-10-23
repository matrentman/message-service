package com.mtlogic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoreMessageService {
	final Logger logger = LoggerFactory.getLogger(StoreMessageService.class);
	
	private static final String CRYPT_KEY = "m3$0Th3L10m@!"; 
	private static final byte[] SALT = {-80, -61, 81, -55, 124, -35, 57, -11, -118, -16, -10, -91, 7, -63, 92, -109};
	public static final String DATE_FORMAT = "yyyyMMdd";
	public static final String UNDEFINED = "undefined";
	public static final String NULL = "null";
	public static final String ELIGIBILITY_QA = "eligibility-qa";
	public static final String ELIGIBILITY_PROD = "eligibility-prod";
	
	public StoreMessageService() {
		super();
	}

	public void storeMessage(String payload) {
		logger.info(">>>ENTERED storeMessage()");
		
		Context envContext = null;
		Connection con = null;
		PreparedStatement preparedStatement = null;
		String insertMessageSQL = "insert into public.message (type_id, message, time, patient_id, iv, client_id, user_name, billable, subscriber_identifier, npi, payor_code, date_of_birth, date_of_service_start, date_of_service_end, service_type_code, patient_first_name, patient_last_name, dependent) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		
		final JSONObject obj = new JSONObject(payload);
		String dataSource = obj.getString("dataSource");
		int messageType = obj.getInt("messageType");
		String message = obj.getString("message");
		int patientIdentifier = obj.getInt("patientId");
		Integer clientIdentifier = Integer.parseInt(obj.getString("clientId"));
		String userName = obj.getString("userName");
		String npi = obj.getString("npi");
		String payorCode = obj.getString("payorCode");
		String subscriberIdentifier = obj.getString("subscriberId");
		String dateOfBirth = obj.getString("dateOfBirth");
		String[] datesOfService = obj.getString("dateOfService").split("-");
		String dateOfServiceStart = datesOfService[0];
		String dateOfServiceEnd = null;
		if (datesOfService.length > 1) {
			dateOfServiceEnd = datesOfService[1];
		}
		String serviceTypeCode = obj.getString("serviceTypeCode");
		String firstName = obj.optString("firstName");
		if (firstName != null && (firstName.equals(UNDEFINED) || firstName.equals(NULL) || firstName.trim().equals(""))) {
			firstName = null;
		}
		String lastName = obj.optString("lastName");
		if (lastName != null && (lastName.equals(UNDEFINED) || lastName.equals(NULL) || lastName.trim().equals(""))) {
			lastName = null;
		}
		Boolean dependentFlag = Boolean.valueOf(obj.optString("dependent"));
		
		try {
			envContext = new InitialContext();
			Context initContext  = (Context)envContext.lookup("java:/comp/env");
			DataSource ds = determineDataSource(dataSource);
//			if (dataSource.equalsIgnoreCase(ELIGIBILITY)) {
//			    ds = (DataSource)initContext.lookup("jdbc/eligibility");
//			} else {
//				ds = (DataSource)initContext.lookup("jdbc/claimstatus");
//			}
			con = ds.getConnection();					
			preparedStatement = con.prepareStatement(insertMessageSQL);
			preparedStatement.setInt(1, messageType);
			CryptResult cr = encrypt(message.toString(), generateSecret(CRYPT_KEY.toCharArray(), SALT));
			preparedStatement.setString(2, cr.getEncryptedText());
			long time = System.currentTimeMillis();
			Timestamp timestamp = new java.sql.Timestamp(time);
			preparedStatement.setTimestamp(3, timestamp);
			preparedStatement.setInt(4, patientIdentifier);
			preparedStatement.setBytes(5, cr.getIv());
			preparedStatement.setInt(6, clientIdentifier);
			preparedStatement.setString(7, userName);
			preparedStatement.setBoolean(8, (messageType == 4 && !message.contains("~AAA"))?true:false);
			preparedStatement.setString(9, subscriberIdentifier);
			preparedStatement.setString(10, npi);
			preparedStatement.setString(11, payorCode);
			if (dateOfBirth.length() != 8) {
				SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
				Calendar cal = Calendar.getInstance();
				dateOfBirth = sdf.format(cal.getTime()); 
			}
			String dob = dateOfBirth.substring(0,4) + "-" + dateOfBirth.substring(4,6) + "-" + dateOfBirth.substring(6, dateOfBirth.length());
			preparedStatement.setDate(12, java.sql.Date.valueOf(dob));
			String doss = dateOfServiceStart.substring(0,4) + "-" + dateOfServiceStart.substring(4,6) + "-" + dateOfServiceStart.substring(6, dateOfServiceStart.length());
			preparedStatement.setDate(13, java.sql.Date.valueOf(doss));
			String dose = null;
			if (dateOfServiceEnd != null) {
				dose = dateOfServiceEnd.substring(0,4) + "-" + dateOfServiceEnd.substring(4,6) + "-" + dateOfServiceEnd.substring(6, dateOfServiceEnd.length());
				preparedStatement.setDate(14, java.sql.Date.valueOf(dose));
			} else {
				preparedStatement.setNull(14, java.sql.Types.DATE);
			}
			preparedStatement.setString(15, serviceTypeCode);
			
			if (firstName != null) {
				preparedStatement.setString(16, firstName);
			} else {
				preparedStatement.setNull(16, java.sql.Types.CHAR);
			}
			if (lastName != null) {
				preparedStatement.setString(17, lastName);
			} else {
				preparedStatement.setNull(17, java.sql.Types.CHAR);
			}
			preparedStatement.setBoolean(18, dependentFlag);
			
			preparedStatement.executeUpdate();		
		} catch (Exception e) {
			e.printStackTrace();
			logger.error("ERROR!!! : " + e.getMessage());
			logger.error("dataSource: " + dataSource);
			logger.error("messageType: " + messageType);
			logger.error("patientIdentifier: " + patientIdentifier);
			logger.error("clientIdentifier: " + clientIdentifier);
			logger.error("userName: " + userName);
			logger.error("npi: " + npi);
			logger.error("payorCode: " + payorCode);
			logger.error("subscriberIdentifier: " + subscriberIdentifier);
			logger.error("dateOfBirth: " + dateOfBirth);
			logger.error("dateOfServiceStart: " + dateOfServiceStart);
			logger.error("dateOfServiceEnd: " + dateOfServiceEnd);
			logger.error("serviceTypeCode: " + serviceTypeCode);
			logger.error("message: " + message);
		} finally {
		    try{preparedStatement.close();}catch(Exception e){};
		    try{con.close();}catch(Exception e){};
		}
		
		logger.info("<<<EXITED storeMessage()");
	}
	
	public String retrieveMessageFromCache(String payload) {
		logger.info(">>>ENTERED retrieveMessageFromCache()");
		Context envContext = null;
		Connection con = null;
		PreparedStatement preparedStatement = null;
		ResultSet rs = null;
		CryptResult cryptResult = new CryptResult();
		
		final JSONObject obj = new JSONObject(payload);
		String dataSource = obj.getString("dataSource");
		String npi = obj.getString("npi");
		String payorCode = obj.getString("payorCode");
		String subscriberIdentifier = obj.getString("subscriberId");
		String dateOfBirth = obj.getString("dateOfBirth");
		String[] datesOfService = obj.getString("dateOfService").split("-");
		String dateOfServiceStart = datesOfService[0];
		String dateOfServiceEnd = null;
		if (datesOfService.length > 1) {
			dateOfServiceEnd = datesOfService[1];
		}
		String serviceTypeCode = obj.getString("serviceTypeCode");
		String firstName = obj.optString("firstName");
		if (firstName != null && (firstName.equals(UNDEFINED) || firstName.equals(NULL) || firstName.trim().equals(""))) {
			firstName = null;
		}
		String lastName = obj.optString("lastName");
		if (lastName != null && (lastName.equals(UNDEFINED) || lastName.equals(NULL) || lastName.trim().equals(""))) {
			lastName = null;
		}
		Boolean dependentFlag = Boolean.valueOf(obj.optString("dependent"));
		
		//String selectMessageSQL = "select message, iv from public.message where billable = true and type_id = ? and subscriber_identifier = ? and payor_code = ? and npi = ? and date_of_birth = ? and date_of_service = ?";
		String selectMessageSQL = "select message, iv from public.message where billable = true and type_id = ? and subscriber_identifier = ? and payor_code = ? and npi = ? and service_type_code = ? and date_of_birth = to_date(?, 'YYYY-MM-DD') and date_of_service_start = to_date(?, 'YYYY-MM-DD')";
		if (dateOfServiceEnd == null) {
			selectMessageSQL = selectMessageSQL + " and date_of_service_end is null";
		} else {
			selectMessageSQL = selectMessageSQL + " and date_of_service_end = to_date(?, 'YYYY-MM-DD')";
		}
		if (lastName == null) {
			selectMessageSQL = selectMessageSQL + " and patient_last_name is null";
		} else {
			selectMessageSQL = selectMessageSQL + " and patient_last_name = ?";
		}
		if (firstName == null) {
			selectMessageSQL = selectMessageSQL + " and patient_first_name is null";
		} else {
			selectMessageSQL = selectMessageSQL + " and patient_first_name = ?";
		}
		selectMessageSQL = selectMessageSQL + " and dependent = ?";
		selectMessageSQL = selectMessageSQL + " and time > NOW() - INTERVAL '8 hours'";
				
		try {
			envContext = new InitialContext();
			Context initContext  = (Context)envContext.lookup("java:/comp/env");
			DataSource ds = determineDataSource(dataSource);
//			if (dataSource.equalsIgnoreCase(ELIGIBILITY)) {
//			    ds = (DataSource)initContext.lookup("jdbc/eligibility");
//			} else {
//				ds = (DataSource)initContext.lookup("jdbc/claimstatus");
//			}
			int parameterIndex = 1;
			con = ds.getConnection();					
			preparedStatement = con.prepareStatement(selectMessageSQL);
			preparedStatement.setInt(parameterIndex++, 4);
			preparedStatement.setString(parameterIndex++, subscriberIdentifier);
			preparedStatement.setString(parameterIndex++, payorCode);
			preparedStatement.setString(parameterIndex++, npi);
			preparedStatement.setString(parameterIndex++, serviceTypeCode);
			String dob = dateOfBirth.substring(0,4) + "-" + dateOfBirth.substring(4,6) + "-" + dateOfBirth.substring(6, dateOfBirth.length());
			//preparedStatement.setDate(5, java.sql.Date.valueOf(dob));
			preparedStatement.setString(parameterIndex++, dob);
			String doss = dateOfServiceStart.substring(0,4) + "-" + dateOfServiceStart.substring(4,6) + "-" + dateOfServiceStart.substring(6, dateOfServiceStart.length());
			preparedStatement.setString(parameterIndex++, doss);
			if (dateOfServiceEnd != null) {
				String dose = dateOfServiceEnd.substring(0,4) + "-" + dateOfServiceEnd.substring(4,6) + "-" + dateOfServiceEnd.substring(6, dateOfServiceEnd.length());
				preparedStatement.setString(parameterIndex++, dose);
			}
			if (lastName != null) {
				preparedStatement.setString(parameterIndex++, lastName);
			} 
			if (firstName != null) {
				preparedStatement.setString(parameterIndex++, firstName);
			} 
			preparedStatement.setBoolean(parameterIndex++, dependentFlag);
			
			rs = preparedStatement.executeQuery();
			
			if (rs.next()) {
				cryptResult.setEncryptedText(new String(rs.getString("message")));
				cryptResult.setIv(rs.getBytes("iv"));
			} else {
				
			}	
		} catch (SQLException e) {
			e.printStackTrace();
			System.out.println("SQLException! : " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Exception! : " + e.getMessage());
		} finally {
			try{rs.close();}catch(Exception e){};
			try{preparedStatement.close();}catch(Exception e){};
			try{con.close();}catch(Exception e){};
		}
		String returnMessage = null;
		logger.info("<<<EXITED retrieveMessageFromCache()");
		if (cryptResult.getEncryptedText()!=null && !cryptResult.getEncryptedText().isEmpty()) {
			returnMessage = decryptMessage(cryptResult);
		}
		return returnMessage;
	}
	
	private SecretKey generateSecret(char[] password, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
		logger.info(">>>ENTERED generateSecret()");
		// hack for JCE Unlimited Strength due to bug in JDK (https://bugs.openjdk.java.net/browse/JDK-8149417)
	    Field field;
		try {
			field = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted");
		
			field.setAccessible(true);

			Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

			field.set(null, false);
		} catch (NoSuchFieldException | SecurityException | ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		
		SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		KeySpec spec = new PBEKeySpec(password, salt, 65536, 256);
		SecretKey tmp = factory.generateSecret(spec);
		SecretKey secret = new SecretKeySpec(tmp.getEncoded(), "AES");
		
		logger.info(">>>EXITED generateSecret()");
		return secret;
	}
	
	private CryptResult encrypt(String plainText, SecretKey secret) throws Exception{
		logger.info(">>>ENTERED encrypt()");
		CryptResult cryptResult = new CryptResult();
		
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, secret);
			AlgorithmParameters params = cipher.getParameters();
			byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
			cryptResult.setIv(iv);
			byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));
			
			Base64.Encoder encoder = Base64.getEncoder();
			cryptResult.setEncryptedText(encoder.encodeToString(cipherText));
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e);
		}
		
		logger.info(">>>EXITED encrypt()");
		return cryptResult;
	}
	
	public String decrypt(String encryptedText, SecretKey secret, byte[] iv) throws Exception{
		String plainText = "";
		
		try {
			Base64.Decoder decoder = Base64.getDecoder();
			byte[] encryptedBytes = decoder.decode(encryptedText);
			
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
			plainText = new String(cipher.doFinal(encryptedBytes), "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e);
		}
		
		return plainText;
	}
	
	public String decryptMessage(CryptResult cr) {
		SecretKey secret;
		String plainTextMessage = null;
		try {
			secret = generateSecret(CRYPT_KEY.toCharArray(), SALT);
			plainTextMessage = decrypt(cr.getEncryptedText(), secret, cr.getIv());
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return plainTextMessage;
	}
	
	private DataSource determineDataSource(String dataSource) {
		DataSource ds = null;
		try {
			Context envContext = new InitialContext();
			Context initContext  = (Context)envContext.lookup("java:/comp/env");
			if (ELIGIBILITY_QA.equalsIgnoreCase(dataSource)) {
			    ds = (DataSource)initContext.lookup("jdbc/eligibility-qa");
			} else if (ELIGIBILITY_PROD.equalsIgnoreCase(dataSource)) {
				ds = (DataSource)initContext.lookup("jdbc/eligibility-prod");
			} else {
				ds = (DataSource)initContext.lookup("jdbc/claimstatus");
			}
		} catch (NamingException e) {
			e.printStackTrace();
		}
		return ds;
	}
	
}
