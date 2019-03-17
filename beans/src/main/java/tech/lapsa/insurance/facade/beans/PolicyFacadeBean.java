package tech.lapsa.insurance.facade.beans;

import java.util.Currency;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.CalculationData;
import com.lapsa.insurance.domain.CompanyData;
import com.lapsa.insurance.domain.InsurancePeriodData;
import com.lapsa.insurance.domain.InsurantData;
import com.lapsa.insurance.domain.PersonalData;
import com.lapsa.insurance.domain.policy.Policy;

import tech.lapsa.esbd.dao.NotFound;
import tech.lapsa.esbd.dao.elements.InsuranceClassTypeService;
import tech.lapsa.esbd.dao.elements.InsuranceClassTypeService.InsuranceClassTypeServiceRemote;
import tech.lapsa.esbd.dao.entities.PolicyEntityService.PolicyEntityServiceRemote;
import tech.lapsa.esbd.domain.entities.PolicyEntity;
import tech.lapsa.esbd.domain.entities.SubjectCompanyEntity;
import tech.lapsa.esbd.domain.entities.SubjectPersonEntity;
import tech.lapsa.insurance.facade.PolicyFacade;
import tech.lapsa.insurance.facade.PolicyFacade.PolicyFacadeLocal;
import tech.lapsa.insurance.facade.PolicyFacade.PolicyFacadeRemote;
import tech.lapsa.insurance.facade.PolicyNotFound;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyStreams;
import tech.lapsa.java.commons.function.MyStrings;

@Stateless(name = PolicyFacade.BEAN_NAME)
public class PolicyFacadeBean implements PolicyFacadeLocal, PolicyFacadeRemote {

    private static final Currency KZT = Currency.getInstance("KZT");

    // READERS

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Policy getByNumber(final String number) throws PolicyNotFound, IllegalArgument {
        try {
            return _getByNumber(number);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgument(e);
        }
    }

    @EJB
    private PolicyEntityServiceRemote policyService;

    private Policy _getByNumber(final String number) throws IllegalArgumentException, PolicyNotFound {
        MyStrings.requireNonEmpty(number, "number");

        final PolicyEntity p;
        try {
            p = policyService.getByNumber(number);
        } catch (final IllegalArgument e) {
            // it should not happens
            throw new EJBException(e.getMessage());
        } catch (final NotFound e) {
            throw MyExceptions.format(PolicyNotFound::new, "Policy not found with number %1$s", number);
        }

        return _fillFromESBDEntity(p);
    }

    //

    @EJB
    private InsuranceClassTypeServiceRemote insuranceClassTypeService;

    private Policy _fillFromESBDEntity(final PolicyEntity in) {
        return fillFromESBDEntity(in, insuranceClassTypeService);
    }

    static Policy fillFromESBDEntity(final PolicyEntity in, final InsuranceClassTypeService insuranceClassTypeService) {
        if (in == null)
            return new Policy();

        final Policy out = new Policy();

        // in.getBranch();
        // in.getCalculatedPremium();
        out.setCalculation(new CalculationData());
        out.getCalculation().setAmount(in.getCalculatedPremium());
        out.getCalculation().setCurrency(KZT);

        out.setActual(new CalculationData());
        out.getActual().setAmount(in.getActualPremium());
        out.getActual().setCurrency(KZT);

        out.setPaymentDate(in.getPaymentDate());
        out.setPolicyDate(in.getPolicyDate());

        // in.getId();
        out.setNumber(in.getNumber());

        out.setDateOfIssue(in.getDateOfIssue());

        // in.getCreated();
        // in.getInternalNumber();

        if (in.getInsurant() != null) {

            out.setInsurant(new InsurantData());

            if (in.getInsurant().getContact() != null) {
                out.getInsurant().setPhone(in.getInsurant().getContact().getPhone());
                out.getInsurant().setEmail(in.getInsurant().getContact().getEmail());
                // in.getInsurant().getContact().getHomeAdress();
                // in.getInsurant().getContact().getSiteUrl();
            }

            out.getInsurant().setIdNumber(in.getInsurant().getIdNumber());

            // in.getInsurant().getComments();

            // in.getInsurant().getEconomicsSector();
            // in.getInsurant().getId();
            // in.getInsurant().getOrigin().getCity();
            // in.getInsurant().getOrigin().getCountry();
            // in.getInsurant().getSubjectType();
            // in.getInsurant().getTaxPayerNumber();

            if (in.getInsurant() instanceof SubjectPersonEntity) {

                final SubjectPersonEntity in1 = (SubjectPersonEntity) in.getInsurant();

                if (in1.getPersonal() != null) {
                    out.getInsurant().setPersonal(new PersonalData());
                    out.getInsurant().getPersonal().setName(in1.getPersonal().getName());
                    out.getInsurant().getPersonal().setSurename(in1.getPersonal().getSurename());
                    out.getInsurant().getPersonal().setPatronymic(in1.getPersonal().getPatronymic());
                    out.getInsurant().getPersonal().setDateOfBirth(in1.getPersonal().getDayOfBirth());
                    out.getInsurant().getPersonal().setGender(in1.getPersonal().getGender());
                }

                // in1.getIdentityCard().getDateOfIssue();
                // in1.getIdentityCard().getIdentityCardType();
                // in1.getIdentityCard().getIssuingAuthority();
                // in1.getIdentityCard().getNumber();
                // in1.getTaxPayerNumber();
            }

            if (in.getInsurant() instanceof SubjectCompanyEntity) {
                final SubjectCompanyEntity in1 = (SubjectCompanyEntity) in.getInsurant();

                out.getInsurant().setCompany(new CompanyData());
                out.getInsurant().getCompany().setName(in1.getCompanyName());

                // in1.getHeadName();
                // in1.getAccountantName();
                // in1.getCompanyActivityKind();
            }
        }

        out.setDateOfTermination(in.getDateOfCancelation());
        out.setTerminationReason(in.getCancelationReasonType());

        // in.getReissuedPolicyId();
        // in.getComments();
        // in.getModified();

        in.getInsurer();

        // in.getInsuredDrivers();
        MyStreams.orEmptyOf(in.getInsuredDrivers())
                .map(x -> PolicyDriverFacadeBean.__fillFromESBDEntity(x, insuranceClassTypeService))
                .forEach(out::addDriver);

        // in.getInsuredVehicles();
        MyStreams.orEmptyOf(in.getInsuredVehicles()).map(PolicyVehicleFacadeBean::__fillFromESBDEntity)
                .forEach(out::addVehicle);

        // in.getValidFrom();
        // in.getValidTill();
        out.setPeriod(new InsurancePeriodData());
        out.getPeriod().setFrom(in.getValidFrom());
        out.getPeriod().setTo(in.getValidTill());

        return out;
    }
}
