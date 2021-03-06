/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openmrs.module.limsemrops.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.openmrs.Cohort;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.limsemrops.omodmodels.VLSampleCollectionBatchManifest;
import org.openmrs.module.limsemrops.omodmodels.VLSampleInformation;
import org.openmrs.module.limsemrops.utility.ConstantUtils;
import org.openmrs.module.limsemrops.utility.LabFormUtils;
import org.openmrs.module.limsemrops.utility.Utils;

/**
 * @author MORRISON.I
 */
public class ViralLoadInfo {
	
	private List<Integer> encounterIDList;
	
	private List<Obs> obsList;
	
	private List<Encounter> encounterList;
	
	private LabFormUtils labFormUtils;
	
	private Map<Integer, String> labMappings;
	
	private Map<Integer, Integer> integerLabMappings;
	
	private Obs rovingObs;
	
	private DBUtility dBUtility;
	
	public ViralLoadInfo(List<Integer> encounterList) {

        this.encounterIDList = encounterList;
        this.obsList = new ArrayList<>();
        this.encounterList = new ArrayList<>();

        labFormUtils = new LabFormUtils();

        labMappings = new HashMap<>();
        integerLabMappings = new HashMap<>();
        this.dBUtility = new DBUtility();

        loadMappings();
        rovingObs = new Obs();

    }
	
	private void loadMappings() {
		labMappings = labFormUtils.getConceptMappings();
		integerLabMappings = labFormUtils.getIntegerConceptMappings();
	}
	
	private String getMappedAnswerValue(int conceptID) {
		if (labMappings.containsKey(conceptID)) {
			return labMappings.get(conceptID);
		}
		return "";
	}
	
	private Integer getIntgerMappedAnswerValue(int conceptID) {
		if (integerLabMappings.containsKey(conceptID)) {
			return integerLabMappings.get(conceptID);
		}
		return null;
	}
	
	public VLSampleCollectionBatchManifest getRecentSampleCollectedManifest() {

        Patient patient = null;
        VLSampleCollectionBatchManifest vLSampleCollectionBatchManifest
                = new VLSampleCollectionBatchManifest();

        List<VLSampleInformation> vLSampleInformations = new ArrayList<>();

        fillUpEncounters(encounterIDList);

        for (Encounter e : encounterList) {
            System.out.println("Processing encounter " + e.getEncounterId());

            patient = e.getPatient();
            Set<Obs> obsSet = e.getAllObs();
            List<Obs> tempObs = new ArrayList<>();
            tempObs.addAll(obsSet);
            System.out.println("Temp obs contains elements " + tempObs.size());

            if (tempObs.stream().map(Obs::getConcept).map(Concept::getConceptId).
                    collect(Collectors.toList())
                    .contains(LabFormUtils.VIRAL_LOAD_REQUEST)) {
                obsList.addAll(obsSet);

                System.out.println("Obs list contains VL Load request");

                VLSampleInformation vLSampleInformation = extractVLInfo(patient, e);
                vLSampleInformations.add(vLSampleInformation);
            }

        }

        String temString = UUID.randomUUID().toString();
        vLSampleCollectionBatchManifest.setManifestID(temString.substring(1, 15).toUpperCase());
        vLSampleCollectionBatchManifest.setReceivingLabID("LIMS-001-98"); // todo
        vLSampleCollectionBatchManifest.setReceivingLabName("Test Lab"); // todo
        vLSampleCollectionBatchManifest.setSendingFacilityID(Utils.getFacilityDATIMId());
        vLSampleCollectionBatchManifest.setSendingFacilityName(Utils.getFacilityName());

        vLSampleCollectionBatchManifest.setSampleInformation(vLSampleInformations);

        return vLSampleCollectionBatchManifest;

    }
	
	private VLSampleInformation extractVLInfo(Patient p, Encounter e) {
		
		VLSampleInformation vLSampleInformation = new VLSampleInformation();
		PatientDemographics patientDemographics = new PatientDemographics(p);
		vLSampleInformation = patientDemographics.fillUpDemographics();
		
		if (!this.obsList.isEmpty()) {
			//sample ID
			rovingObs = Utils.extractObs(LabFormUtils.SAMPLE_TYPE, this.obsList);
			if (rovingObs != null && rovingObs.getValueCoded() != null) {
				vLSampleInformation.setSampleType(getMappedAnswerValue(rovingObs.getValueCoded().getConceptId()));
			}
			
			// indication for VL
			rovingObs = Utils.extractObs(LabFormUtils.INDICATION_FOR_VL, this.obsList);
			if (rovingObs != null && rovingObs.getValueCoded() != null) {
				vLSampleInformation
				        .setIndicationVLTest(getIntgerMappedAnswerValue(rovingObs.getValueCoded().getConceptId()));
			}
			
			vLSampleInformation.setArtCommencementDate(getPatientARTStartDate(p));
			
			String patientLastRegimen = getPatientLatestEncounter(p);
			if (patientLastRegimen != null) {
				vLSampleInformation.setDrugRegimen(patientLastRegimen);
			}
			
			//  vLSampleInformation.setPregnantBreastfeadingStatus();
			//rovingObs = Utils.extractObs(LabFormUtils., obsList)
			vLSampleInformation.setSampleCollectedBy(e.getEncounterProviders().stream().findFirst().get().getProvider()
			        .getName());
			
			// sample collection date
			rovingObs = Utils.extractObs(LabFormUtils.SAMPLE_COLLECTION_DATE, this.obsList);
			if (rovingObs != null) {
				vLSampleInformation.setSampleCollectionDate(rovingObs.getValueDate());
				vLSampleInformation.setSampleCollectionTime(rovingObs.getValueDatetime());
			}
			
			// sample ID
			rovingObs = Utils.extractObs(LabFormUtils.SAMPLE_ID, this.obsList);
			if (rovingObs != null) {
				vLSampleInformation.setSampleID(rovingObs.getValueText());
			}
			
			//order date
			rovingObs = Utils.extractObs(LabFormUtils.DATE_SAMPLE_ORDERED, this.obsList);
			if (rovingObs != null) {
				vLSampleInformation.setSampleOrderDate(rovingObs.getValueDate());
			}
			
			//order by
			rovingObs = Utils.extractObs(LabFormUtils.REPORTED_BY, this.obsList);
			if (rovingObs != null) {
				vLSampleInformation.setSampleOrderedBy(rovingObs.getValueText());
			}
			
			//date sample sent
			rovingObs = Utils.extractObs(LabFormUtils.DATE_SAMPLE_SENT_TO_PCR_LAB, this.obsList);
			if (rovingObs != null) {
				vLSampleInformation.setDateSampleSent(rovingObs.getValueDate());
			}
			
		}
		
		return vLSampleInformation;
		
	}
	
	private void fillUpEncounters(List<Integer> encs) {

        encounterList.clear();

        encs.stream().forEach(a -> {

            Encounter each = null;
            each = Context.getEncounterService().getEncounter(a);

            if (each != null) {
                encounterList.add(each);
            }

        });

    }
	
	private Date getPatientARTStartDate(Patient patient) {
        List<Integer> encounters = dBUtility.getEnrollmentAndPharmacy(patient);
        List<Encounter> hivPharmEncounters = new ArrayList<>();

        encounters.stream().forEach(a -> {

            Encounter each = null;
            each = Context.getEncounterService().getEncounter(a);

            if (each != null) {
                hivPharmEncounters.add(each);
            }

        });
        List<Obs> obsList = new ArrayList<>();
        obsList = Utils.getObsbyEncounter(hivPharmEncounters);

        return Utils.extractARTStartDate(patient, obsList);

    }
	
	private String getPatientLatestEncounter(Patient p) {
		Encounter latestPharmEncounter = Utils.getPatientLastEncounter(p, ConstantUtils.Pharmacy_Encounter_Type_Id);
		String regimenCode = null;
		if (latestPharmEncounter != null) {
			regimenCode = Utils.getPatientLastRegimenByEncounter(latestPharmEncounter);
		}
		
		return regimenCode;
	}
}
