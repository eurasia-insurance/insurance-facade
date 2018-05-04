package tech.lapsa.insurance.facade.beans;

import java.time.LocalDate;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.ContactData;
import com.lapsa.insurance.domain.DriverLicenseData;
import com.lapsa.insurance.domain.IdentityCardData;
import com.lapsa.insurance.domain.OriginData;
import com.lapsa.insurance.domain.PersonalData;
import com.lapsa.insurance.domain.ResidenceData;
import com.lapsa.insurance.domain.policy.PolicyDriver;
import com.lapsa.insurance.elements.InsuranceClassType;
import com.lapsa.insurance.elements.InsuredAgeClass;
import com.lapsa.insurance.elements.Sex;

import tech.lapsa.esbd.dao.NotFound;
import tech.lapsa.esbd.dao.elements.InsuranceClassTypeService;
import tech.lapsa.esbd.dao.elements.InsuranceClassTypeService.InsuranceClassTypeServiceRemote;
import tech.lapsa.esbd.dao.entities.InsuredDriverEntity;
import tech.lapsa.esbd.dao.entities.SubjectPersonEntity;
import tech.lapsa.esbd.dao.entities.SubjectPersonEntityService.SubjectPersonEntityServiceRemote;
import tech.lapsa.insurance.facade.PolicyDriverFacade;
import tech.lapsa.insurance.facade.PolicyDriverFacade.PolicyDriverFacadeLocal;
import tech.lapsa.insurance.facade.PolicyDriverFacade.PolicyDriverFacadeRemote;
import tech.lapsa.insurance.facade.PolicyDriverNotFound;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.kz.taxpayer.TaxpayerNumber;

@Stateless(name = PolicyDriverFacade.BEAN_NAME)
public class PolicyDriverFacadeBean implements PolicyDriverFacadeLocal, PolicyDriverFacadeRemote {

    // READERS

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public InsuranceClassType getDefaultInsuranceClass() {
	return _getDefaultInsuranceClass();
    }

    @EJB
    private InsuranceClassTypeServiceRemote insuranceClassTypeService;

    private InsuranceClassType _getDefaultInsuranceClass() {
	return insuranceClassTypeService.getDefault();
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyDriver getByTaxpayerNumber(final TaxpayerNumber idNumber)
	    throws IllegalArgument, PolicyDriverNotFound {
	try {
	    return _getByTaxpayerNumber(idNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @EJB
    private SubjectPersonEntityServiceRemote subjectPersonService;

    private PolicyDriver _getByTaxpayerNumber(final TaxpayerNumber idNumber)
	    throws IllegalArgumentException, PolicyDriverNotFound {
	MyObjects.requireNonNull(idNumber, "idNumber");

	final SubjectPersonEntity sp;
	try {
	    sp = subjectPersonService.getFirstByIdNumber(idNumber);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	} catch (final NotFound e) {
	    throw MyExceptions.format(PolicyDriverNotFound::new, "Driver not found with idNumber %1$s", idNumber);
	}

	final PolicyDriver pd = _fillFromESBDEntity(sp);
	_fillFromTaxpayerNumber(pd, idNumber);
	return pd;
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyDriver getByTaxpayerNumberOrDefault(final TaxpayerNumber taxpayerNumber)
	    throws IllegalArgument {
	try {
	    return _getByTaxpayerNumberOrDefault(taxpayerNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    private PolicyDriver _getByTaxpayerNumberOrDefault(final TaxpayerNumber taxpayerNumber)
	    throws IllegalArgumentException {
	try {
	    return _getByTaxpayerNumber(taxpayerNumber);
	} catch (final PolicyDriverNotFound e) {
	    final PolicyDriver pd = new PolicyDriver();
	    _fillFromTaxpayerNumber(pd, taxpayerNumber);
	    return pd;
	}
    }

    //

    @Override
    @Deprecated
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public void fetch(final PolicyDriver driver) throws IllegalArgument, PolicyDriverNotFound {
	try {
	    _fetch(driver);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Deprecated
    private void _fetch(final PolicyDriver driver) throws IllegalArgumentException, PolicyDriverNotFound {
	MyObjects.requireNonNull(driver, "driver");
	_clearFetched(driver);

	final PolicyDriver fetched = _getByTaxpayerNumber(driver.getIdNumber());
	if (fetched == null)
	    return;

	driver.setFetched(fetched.isFetched());

	driver.setInsuranceClassType(fetched.getInsuranceClassType());
	driver.setAgeClass(fetched.getAgeClass());

	driver.setPersonalData(fetched.getPersonalData());
	driver.setResidenceData(fetched.getResidenceData());
	driver.setOriginData(fetched.getOriginData());
	driver.setIdentityCardData(fetched.getIdentityCardData());
	driver.setTaxPayerNumber(fetched.getTaxPayerNumber());
	driver.setContactData(fetched.getContactData());
    }

    //

    @Override
    @Deprecated
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public void clearFetched(final PolicyDriver driver) throws IllegalArgument {
	try {
	    _clearFetched(driver);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    private void _clearFetched(final PolicyDriver driver) throws IllegalArgumentException {
	MyObjects.requireNonNull(driver, "driver");
	driver.setFetched(false);

	driver.setInsuranceClassType(_getDefaultInsuranceClass());
	driver.setAgeClass(null);

	driver.setPersonalData(new PersonalData());
	driver.setResidenceData(new ResidenceData());
	driver.setOriginData(new OriginData());
	driver.setIdentityCardData(new IdentityCardData());
	driver.setTaxPayerNumber(null);
	driver.setContactData(new ContactData());
    }

    //

    static PolicyDriver __fillFromESBDEntity(final InsuredDriverEntity in,
	    final InsuranceClassTypeService insuranceClassTypeService) {

	if (in == null)
	    return new PolicyDriver();

	final PolicyDriver out = fillFromESBDEntity(in.getInsuredPerson(), insuranceClassTypeService);

	out.setAgeClass(in.getAgeClass());
	out.setExpirienceClass(in.getExpirienceClass());
	out.setInsuranceClassType(in.getInsuraceClassType());

	final boolean hasAnyPrivilege = MyObjects.nonNull(in.getPrivilegerInfo())
		|| MyObjects.nonNull(in.getGpwParticipantInfo())
		|| MyObjects.nonNull(in.getHandicappedInfo())
		|| MyObjects.nonNull(in.getPensionerInfo());
	out.setHasAnyPrivilege(hasAnyPrivilege);

	if (in.getDriverLicense() != null) {
	    out.setDriverLicenseData(new DriverLicenseData());
	    out.getDriverLicenseData().setDateOfIssue(in.getDriverLicense().getDateOfIssue());
	    out.getDriverLicenseData().setNumber(in.getDriverLicense().getNumber());
	}

	// in.getCreated();
	// in.getId();
	// in.getInsuredAgeExpirienceClass();
	// in.getInsurer();
	// in.getMaritalStatus();
	// in.getModified();
	// in.getPolicy();

	return out;

    }

    //

    private PolicyDriver _fillFromESBDEntity(final SubjectPersonEntity in) {
	return fillFromESBDEntity(in, insuranceClassTypeService);
    }

    static PolicyDriver fillFromESBDEntity(final SubjectPersonEntity in,
	    final InsuranceClassTypeService insuranceClassTypeService) {

	final PolicyDriver driver = new PolicyDriver();

	if (in != null) {

	    if (in.getIdNumber() != null)
		driver.setIdNumber(in.getIdNumber());

	    InsuranceClassType insuranceClassTypeLocal = null;
	    {
		insuranceClassTypeLocal = insuranceClassTypeService.getDefault();
		try {
		    insuranceClassTypeLocal = insuranceClassTypeService.getForSubject(in);
		} catch (final NotFound | IllegalArgument e) {
		}
	    }

	    LocalDate dobLocal = null;
	    {
		if (in != null && in.getPersonal() != null
			&& in.getPersonal().getDayOfBirth() != null)
		    dobLocal = in.getPersonal().getDayOfBirth();
	    }

	    InsuredAgeClass insuredAgeClassLocal = null;
	    {
		if (dobLocal != null)
		    insuredAgeClassLocal = obtainInsuredAgeClass(dobLocal);
	    }

	    Sex genderLocal = null;
	    {
		if (in != null && in.getPersonal() != null
			&& in.getPersonal().getGender() != null)
		    genderLocal = in.getPersonal().getGender();
	    }

	    driver.setIdNumber(in.getIdNumber());

	    driver.setInsuranceClassType(insuranceClassTypeLocal);
	    driver.setAgeClass(insuredAgeClassLocal);

	    driver.getPersonalData().setDateOfBirth(dobLocal);
	    driver.getPersonalData().setGender(genderLocal);

	    if (in != null) {
		driver.setFetched(true);

		if (in.getPersonal() != null) {
		    driver.getPersonalData().setName(in.getPersonal().getName());
		    driver.getPersonalData().setSurename(in.getPersonal().getSurename());
		    driver.getPersonalData().setPatronymic(in.getPersonal().getPatronymic());
		}

		if (in.getOrigin() != null) {
		    driver.getResidenceData().setResident(in.getOrigin().isResident());
		    driver.getOriginData().setCountry(in.getOrigin().getCountry());
		}

		if (in.getContact() != null)
		    driver.getResidenceData().setAddress(in.getContact().getHomeAdress());

		if (in.getOrigin().getCity() != null)
		    driver.getResidenceData().setCity(in.getOrigin().getCity());

		if (in.getIdentityCard() != null) {
		    driver.getIdentityCardData().setNumber(in.getIdentityCard().getNumber());
		    driver.getIdentityCardData().setDateOfIssue(in.getIdentityCard().getDateOfIssue());
		    driver.getIdentityCardData().setType(in.getIdentityCard().getIdentityCardType());
		    driver.getIdentityCardData()
			    .setIssuingAuthority(in.getIdentityCard().getIssuingAuthority());
		}

		if (in.getContact() != null) {
		    driver.getContactData().setEmail(in.getContact().getEmail());
		    driver.getContactData().setPhone(in.getContact().getPhone());
		    driver.getContactData().setSiteUrl(in.getContact().getSiteUrl());
		}

		driver.setTaxPayerNumber(in.getTaxPayerNumber());
	    }
	}

	return driver;
    }

    //

    private PolicyDriver _fillFromTaxpayerNumber(final PolicyDriver driver, final TaxpayerNumber taxpayerNumber) {
	return fillFromTaxpayerNumber(driver, taxpayerNumber, _getDefaultInsuranceClass());
    }

    static PolicyDriver fillFromTaxpayerNumber(final PolicyDriver driver, final TaxpayerNumber taxpayerNumber,
	    InsuranceClassType defaultInsuranceClassType) {

	if (driver.getIdNumber() == null)
	    driver.setIdNumber(taxpayerNumber);

	if (driver.getInsuranceClassType() == null)
	    driver.setInsuranceClassType(defaultInsuranceClassType);

	if (driver.getPersonalData().getDateOfBirth() == null)
	    taxpayerNumber.optionalDateOfBirth() //
		    .ifPresent(driver.getPersonalData()::setDateOfBirth);

	if (driver.getAgeClass() == null)
	    taxpayerNumber.optionalDateOfBirth() //
		    .map(PolicyDriverFacadeBean::obtainInsuredAgeClass)
		    .ifPresent(driver::setAgeClass);

	if (driver.getPersonalData().getGender() == null)
	    taxpayerNumber.optionalGender()
		    .map(PolicyDriverFacadeBean::convertKZLibSex)
		    .ifPresent(driver.getPersonalData()::setGender);

	return driver;
    }

    //

    static Sex convertKZLibSex(final tech.lapsa.kz.taxpayer.Gender kzLibSex) {
	if (kzLibSex == null)
	    return null;
	switch (kzLibSex) {
	case FEMALE:
	    return Sex.FEMALE;
	case MALE:
	    return Sex.MALE;
	}
	return null;
    }

    static InsuredAgeClass obtainInsuredAgeClass(final LocalDate dayOfBirth) {
	if (dayOfBirth == null)
	    return null;
	final int years = calculateAgeByDOB(dayOfBirth);
	return obtainInsuredAgeClass(years);
    }

    static int calculateAgeByDOB(final LocalDate dob) {
	if (dob == null)
	    throw new NullPointerException();
	return dob.until(LocalDate.now()).getYears();
    }

    static InsuredAgeClass obtainInsuredAgeClass(final int years) {
	return years < 25 ? InsuredAgeClass.UNDER25 : InsuredAgeClass.OVER25;
    }
}
