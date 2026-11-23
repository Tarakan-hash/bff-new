package sb.broker.bff.temporal;

import sb.broker.bff.policy.SyncOrgPoliciesWorkflow;
import sb.broker.bff.policy.dto.SyncOrgPoliciesWorkflowResult;
import sb.broker.bff.workflow.api.access.CheckLkAccessInput;
import sb.broker.bff.workflow.api.access.CheckLkAccessResult;
import sb.broker.bff.workflow.api.access.CheckLkAccessWorkflow;
import sb.broker.bff.calculation.dms.dto.AccountDmsCalculationWorkflowInput;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogInput;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogResult;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogWorkflow;
import sb.broker.bff.workflow.api.companyprofile.CompanyProfileResult;
import sb.broker.bff.workflow.api.companyprofile.InitiateCompanyProfileInput;
import sb.broker.bff.workflow.api.companyprofile.InitiateCompanyProfileWorkflow;
import sb.broker.bff.workflow.api.consent.ReadConsentFileInput;
import sb.broker.bff.workflow.api.consent.ReadConsentFileResult;
import sb.broker.bff.workflow.api.consent.ReadConsentFileWorkflow;
import sb.broker.bff.workflow.api.consent.ReadUserConsentsInput;
import sb.broker.bff.workflow.api.consent.ReadUserConsentsResult;
import sb.broker.bff.workflow.api.consent.ReadUserConsentsWorkflow;
import sb.broker.bff.workflow.api.consent.RecallUserConsentInput;
import sb.broker.bff.workflow.api.consent.RecallUserConsentWorkflow;
import sb.broker.bff.workflow.api.consent.SubmitUserConsentsInput;
import sb.broker.bff.workflow.api.consent.SubmitUserConsentsWorkflow;
import sb.broker.bff.workflow.api.lead.SubmitAccountLeadInput;
import sb.broker.bff.workflow.api.lead.SubmitAccountLeadResult;
import sb.broker.bff.workflow.api.lead.SubmitAccountLeadWorkflow;
import sb.broker.bff.workflow.api.policy.SyncPoliciesInput;
import sb.broker.bff.workflow.api.riskprofile.InitiateRiskProfileInput;
import sb.broker.bff.workflow.api.riskprofile.InitiateRiskProfileWorkflow;
import sb.broker.bff.workflow.api.riskprofile.RiskProfileResult;
import sb.broker.bff.workflow.api.calculation.DmsPreviewResult;
import sb.broker.bff.workflow.api.calculation.InitiateAccountDmsCalculationInput;
import sb.broker.bff.workflow.api.calculation.InitiateAccountDmsCalculationWorkflow;
import sb.broker.bff.workflow.api.calculation.InitiateAccountKaskoCalculationInput;
import sb.broker.bff.workflow.api.calculation.InitiateAccountKaskoCalculationWorkflow;
import sb.broker.bff.workflow.api.calculation.KaskoPreviewResult;
import sb.broker.bff.workflow.api.calculation.InitiateAccountOsagoCalculationInput;
import sb.broker.bff.workflow.api.calculation.InitiateAccountOsagoCalculationWorkflow;
import sb.broker.bff.workflow.api.calculation.OsagoPreviewResult;

import java.util.List;

/**
 * Workflow, которые запускает bff-lk.
 */
public final class LkWorkflows {

    public static final WorkflowContract<CheckLkAccessInput, CheckLkAccessResult> CHECK_LK_ACCESS =
            WorkflowContract.of(CheckLkAccessWorkflow.class, CheckLkAccessInput.class, CheckLkAccessResult.class);

    public static final WorkflowContract<ReadUserConsentsInput, ReadUserConsentsResult> READ_USER_CONSENTS =
            WorkflowContract.of(ReadUserConsentsWorkflow.class, ReadUserConsentsInput.class, ReadUserConsentsResult.class);

    public static final WorkflowContract<ReadConsentFileInput, ReadConsentFileResult> READ_CONSENT_FILE =
            WorkflowContract.of(ReadConsentFileWorkflow.class, ReadConsentFileInput.class, ReadConsentFileResult.class);

    public static final WorkflowContract<SubmitUserConsentsInput, Void> SUBMIT_USER_CONSENTS =
            WorkflowContract.of(SubmitUserConsentsWorkflow.class, SubmitUserConsentsInput.class, Void.class);

    public static final WorkflowContract<RecallUserConsentInput, Void> RECALL_USER_CONSENT =
            WorkflowContract.of(RecallUserConsentWorkflow.class, RecallUserConsentInput.class, Void.class);

    public static final WorkflowContract<SubmitAccountLeadInput, SubmitAccountLeadResult> SUBMIT_ACCOUNT_LEAD =
            WorkflowContract.of(SubmitAccountLeadWorkflow.class, SubmitAccountLeadInput.class, SubmitAccountLeadResult.class);

    public static final WorkflowContract<InitiateCompanyProfileInput, CompanyProfileResult> INITIATE_COMPANY_PROFILE =
            WorkflowContract.of(InitiateCompanyProfileWorkflow.class, InitiateCompanyProfileInput.class, CompanyProfileResult.class);

    public static final WorkflowContract<InitiateRiskProfileInput, RiskProfileResult> INITIATE_RISK_PROFILE =
            WorkflowContract.of(InitiateRiskProfileWorkflow.class, InitiateRiskProfileInput.class, RiskProfileResult.class);

    public static final WorkflowContract<ReadProductCatalogInput, ReadProductCatalogResult> READ_PRODUCT_CATALOG =
            WorkflowContract.of(ReadProductCatalogWorkflow.class, ReadProductCatalogInput.class, ReadProductCatalogResult.class);

    public static final WorkflowContract<SyncPoliciesInput, SyncOrgPoliciesWorkflowResult> SYNC_POLICIES =
            WorkflowContract.of(SyncOrgPoliciesWorkflow.class, SyncPoliciesInput.class, SyncOrgPoliciesWorkflowResult.class);

    // BSNP-374: Используем собственный AccountDmsCalculationWorkflowInput вместо InitiateAccountDmsCalculationInput из bff-lk-workflow-api,
    // поскольку ИНН больше не передаётся от фронта, а определяется в WDS по результату проверки допуска
    public static final WorkflowContract<AccountDmsCalculationWorkflowInput, DmsPreviewResult> INITIATE_ACCOUNT_DMS_CALCULATION =
            WorkflowContract.of(InitiateAccountDmsCalculationWorkflow.class, AccountDmsCalculationWorkflowInput.class,
                    DmsPreviewResult.class);

    public static final WorkflowContract<InitiateAccountKaskoCalculationInput, KaskoPreviewResult> INITIATE_ACCOUNT_KASKO_CALCULATION =
            WorkflowContract.of(InitiateAccountKaskoCalculationWorkflow.class, InitiateAccountKaskoCalculationInput.class,
                    KaskoPreviewResult.class);

    public static final WorkflowContract<InitiateAccountOsagoCalculationInput, OsagoPreviewResult> INITIATE_ACCOUNT_OSAGO_CALCULATION =
            WorkflowContract.of(InitiateAccountOsagoCalculationWorkflow.class, InitiateAccountOsagoCalculationInput.class,
                    OsagoPreviewResult.class);

    /** Предрасчёты кабинета: выбор предложения находит свой по идентификатору workflow. */
    public static final List<WorkflowContract<?, ?>> CALCULATIONS = List.of(INITIATE_ACCOUNT_DMS_CALCULATION,
            INITIATE_ACCOUNT_KASKO_CALCULATION, INITIATE_ACCOUNT_OSAGO_CALCULATION);

    private LkWorkflows() {
    }
}
