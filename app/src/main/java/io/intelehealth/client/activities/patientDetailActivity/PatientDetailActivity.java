package io.intelehealth.client.activities.patientDetailActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.crashlytics.android.Crashlytics;

import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import io.intelehealth.client.R;
import io.intelehealth.client.activities.homeActivity.HomeActivity;
import io.intelehealth.client.activities.identificationActivity.IdentificationActivity;
import io.intelehealth.client.activities.visitSummaryActivity.VisitSummaryActivity;
import io.intelehealth.client.activities.vitalActivity.VitalsActivity;
import io.intelehealth.client.app.AppConstants;
import io.intelehealth.client.database.InteleHealthDatabaseHelper;
import io.intelehealth.client.database.dao.EncounterDAO;
import io.intelehealth.client.database.dao.ImagesDAO;
import io.intelehealth.client.database.dao.PatientsDAO;
import io.intelehealth.client.database.dao.VisitsDAO;
import io.intelehealth.client.knowledgeEngine.Node;
import io.intelehealth.client.models.Patient;
import io.intelehealth.client.models.dto.EncounterDTO;
import io.intelehealth.client.models.dto.VisitDTO;
import io.intelehealth.client.utilities.DateAndTimeUtils;
import io.intelehealth.client.utilities.DownloadFilesUtils;
import io.intelehealth.client.utilities.Logger;
import io.intelehealth.client.utilities.NetworkConnection;
import io.intelehealth.client.utilities.SessionManager;
import io.intelehealth.client.utilities.UrlModifiers;
import io.intelehealth.client.utilities.UuidDictionary;
import io.intelehealth.client.utilities.exception.DAOException;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;

import static io.intelehealth.client.app.AppConstants.IMAGE_PATH;

public class PatientDetailActivity extends AppCompatActivity {
    private static final String TAG = PatientDetailActivity.class.getSimpleName();
    String patientName;
    String visitUuid = "";
    String patientUuid;
    String intentTag = "";
    String profileImage = "";
    String profileImage1 = "";
    SessionManager sessionManager = null;
    Patient patient_new = new Patient();

    EncounterDTO encounterDTO = new EncounterDTO();
    PatientsDAO patientsDAO = new PatientsDAO();
    private boolean hasLicense;
    private boolean returning;

    String phistory = "";
    String fhistory = "";
    LinearLayout previousVisitsList;
    String visitValue;
    private String encounterVitals = "";
    private String encounterAdultIntials = "";
    SQLiteDatabase db = null;
    Button editbtn;
    Button newVisit;
    IntentFilter filter;
    Myreceiver reMyreceive;
    ImageView photoView;
    ImagesDAO imagesDAO = new ImagesDAO();
    TextView idView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_detail);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        sessionManager = new SessionManager(this);
        reMyreceive = new Myreceiver();
        filter = new IntentFilter("OpenmrsID");
        newVisit = findViewById(R.id.button_new_visit);
        Intent intent = this.getIntent(); // The intent was passed to the activity
        if (intent != null) {
            patientUuid = intent.getStringExtra("patientUuid");
            patientName = intent.getStringExtra("patientName");

            intentTag = intent.getStringExtra("tag");
            Logger.logD(TAG, "Patient ID: " + patientUuid);
            Logger.logD(TAG, "Patient Name: " + patientName);
            Logger.logD(TAG, "Intent Tag: " + intentTag);
        }
        editbtn = findViewById(R.id.edit_button);
        editbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent2 = new Intent(PatientDetailActivity.this, IdentificationActivity.class);
                intent2.putExtra("patientUuid", patientUuid);
                startActivity(intent2);

            }
        });
        setDisplay(patientUuid);

        newVisit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // before starting, we determine if it is new visit for a returning patient
                // extract both FH and PMH
                SimpleDateFormat currentDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault());
                Date todayDate = new Date();
                String thisDate = currentDate.format(todayDate);


                String uuid = UUID.randomUUID().toString();
                EncounterDAO encounterDAO = new EncounterDAO();
                encounterDTO = new EncounterDTO();
                encounterDTO.setUuid(UUID.randomUUID().toString());
                encounterDTO.setEncounterTypeUuid(encounterDAO.getEncounterTypeUuid("ENCOUNTER_VITALS"));
                encounterDTO.setEncounterTime(thisDate);
                encounterDTO.setVisituuid(uuid);
                encounterDTO.setSyncd(false);
                encounterDTO.setProvideruuid(sessionManager.getProviderID());
                encounterDTO.setVoided(0);
                try {
                    encounterDAO.createEncountersToDB(encounterDTO);
                } catch (DAOException e) {
                    Crashlytics.getInstance().core.logException(e);
                }

                InteleHealthDatabaseHelper mDatabaseHelper = new InteleHealthDatabaseHelper(PatientDetailActivity.this);
                SQLiteDatabase sqLiteDatabase = mDatabaseHelper.getReadableDatabase();

                String CREATOR_ID = sessionManager.getCreatorID();
                returning = false;
                sessionManager.setReturning(returning);

                String[] cols = {"value"};
                Cursor cursor = sqLiteDatabase.query("tbl_obs", cols, "encounteruuid=? and conceptuuid=?",// querying for PMH
                        new String[]{encounterDTO.getUuid(), UuidDictionary.RHK_MEDICAL_HISTORY_BLURB},
                        null, null, null);

                if (cursor.moveToFirst()) {
                    // rows present
                    do {
                        // so that null data is not appended
                        phistory = phistory + cursor.getString(0);

                    }
                    while (cursor.moveToNext());
                    returning = true;
                    sessionManager.setReturning(returning);
                }
                cursor.close();

                Cursor cursor1 = sqLiteDatabase.query("tbl_obs", cols, "encounteruuid=? and conceptuuid=?",// querying for FH
                        new String[]{encounterDTO.getUuid(), UuidDictionary.RHK_MEDICAL_HISTORY_BLURB},
                        null, null, null);
                if (cursor1.moveToFirst()) {
                    // rows present
                    do {
                        fhistory = fhistory + cursor1.getString(0);
                    }
                    while (cursor1.moveToNext());
                    returning = true;
                    sessionManager.setReturning(returning);
                }
                cursor1.close();

                // Will display data for patient as it is present in database
                // Toast.makeText(PatientDetailActivity.this,"PMH: "+phistory,Toast.LENGTH_SHORT).sƒhow();
                // Toast.makeText(PatientDetailActivity.this,"FH: "+fhistory,Toast.LENGTH_SHORT).show();

                Intent intent2 = new Intent(PatientDetailActivity.this, VitalsActivity.class);
                String fullName = patient_new.getFirst_name() + " " + patient_new.getLast_name();
                intent2.putExtra("patientUuid", patientUuid);

                VisitDTO visitDTO = new VisitDTO();

                visitDTO.setUuid(uuid);
                visitDTO.setPatientuuid(patient_new.getUuid());
                visitDTO.setStartdate(thisDate);
                visitDTO.setVisitTypeUuid(UuidDictionary.VISIT_TELEMEDICINE);
                visitDTO.setLocationuuid(sessionManager.getLocationUuid());
                visitDTO.setSyncd(false);
                visitDTO.setCreatoruuid(sessionManager.getCreatorID());//static

                VisitsDAO visitsDAO = new VisitsDAO();

                try {
                    visitsDAO.insertPatientToDB(visitDTO);
                } catch (DAOException e) {
                    Crashlytics.getInstance().core.logException(e);
                }

                // visitUuid = String.valueOf(visitLong);
//                localdb.close();
                intent2.putExtra("patientUuid", patientUuid);
                intent2.putExtra("visitUuid", uuid);
                intent2.putExtra("encounterUuidVitals", encounterDTO.getUuid());
                intent2.putExtra("encounterUuidAdultIntial", "");
                intent2.putExtra("name", fullName);
                intent2.putExtra("tag", "new");
                startActivity(intent2);
            }
        });
    }

    @Override
    protected void onStart() {
        registerReceiver(reMyreceive, filter);
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(reMyreceive);
        super.onDestroy();
    }

    public void setDisplay(String dataString) {
        db = AppConstants.inteleHealthDatabaseHelper.getReadableDatabase();

        String patientSelection = "uuid = ?";
        String[] patientArgs = {dataString};
        String[] patientColumns = {"uuid", "openmrs_id", "first_name", "middle_name", "last_name",
                "date_of_birth", "address1", "address2", "city_village", "state_province",
                "postal_code", "country", "phone_number", "gender", "sdw",
                "patient_photo"};
        Cursor idCursor = db.query("tbl_patient", patientColumns, patientSelection, patientArgs, null, null, null);
        if (idCursor.moveToFirst()) {
            do {
                patient_new.setUuid(idCursor.getString(idCursor.getColumnIndexOrThrow("uuid")));
                patient_new.setOpenmrs_id(idCursor.getString(idCursor.getColumnIndexOrThrow("openmrs_id")));
                patient_new.setFirst_name(idCursor.getString(idCursor.getColumnIndexOrThrow("first_name")));
                patient_new.setMiddle_name(idCursor.getString(idCursor.getColumnIndexOrThrow("middle_name")));
                patient_new.setLast_name(idCursor.getString(idCursor.getColumnIndexOrThrow("last_name")));
                patient_new.setDate_of_birth(idCursor.getString(idCursor.getColumnIndexOrThrow("date_of_birth")));
                patient_new.setAddress1(idCursor.getString(idCursor.getColumnIndexOrThrow("address1")));
                patient_new.setAddress2(idCursor.getString(idCursor.getColumnIndexOrThrow("address2")));
                patient_new.setCity_village(idCursor.getString(idCursor.getColumnIndexOrThrow("city_village")));
                patient_new.setState_province(idCursor.getString(idCursor.getColumnIndexOrThrow("state_province")));
                patient_new.setPostal_code(idCursor.getString(idCursor.getColumnIndexOrThrow("postal_code")));
                patient_new.setCountry(idCursor.getString(idCursor.getColumnIndexOrThrow("country")));
                patient_new.setPhone_number(idCursor.getString(idCursor.getColumnIndexOrThrow("phone_number")));
                patient_new.setGender(idCursor.getString(idCursor.getColumnIndexOrThrow("gender")));
                patient_new.setPatient_photo(idCursor.getString(idCursor.getColumnIndexOrThrow("patient_photo")));
            } while (idCursor.moveToNext());
        }
        idCursor.close();

        String patientSelection1 = "patientuuid = ?";
        String[] patientArgs1 = {dataString};
        String[] patientColumns1 = {"value", "person_attribute_type_uuid"};
        Cursor idCursor1 = db.query("tbl_patient_attribute", patientColumns1, patientSelection1, patientArgs1, null, null, null);
        String name = "";
        if (idCursor1.moveToFirst()) {
            do {
                try {
                    name = patientsDAO.getAttributesName(idCursor1.getString(idCursor1.getColumnIndexOrThrow("person_attribute_type_uuid")));
                } catch (DAOException e) {
                    Crashlytics.getInstance().core.logException(e);
                }

                if (name.equalsIgnoreCase("caste")) {
                    patient_new.setCaste(idCursor1.getString(idCursor1.getColumnIndexOrThrow("value")));
                }
                if (name.equalsIgnoreCase("Telephone Number")) {
                    patient_new.setPhone_number(idCursor1.getString(idCursor1.getColumnIndexOrThrow("value")));
                }
                if (name.equalsIgnoreCase("Education Level")) {
                    patient_new.setEducation_level(idCursor1.getString(idCursor1.getColumnIndexOrThrow("value")));
                }
                if (name.equalsIgnoreCase("Economic Status")) {
                    patient_new.setEconomic_status(idCursor1.getString(idCursor1.getColumnIndexOrThrow("value")));
                }
                if (name.equalsIgnoreCase("occupation")) {
                    patient_new.setOccupation(idCursor1.getString(idCursor1.getColumnIndexOrThrow("value")));
                }
                if (name.equalsIgnoreCase("Son/wife/daughter")) {
                    patient_new.setSdw(idCursor1.getString(idCursor1.getColumnIndexOrThrow("value")));
                }
                if (name.equalsIgnoreCase("ProfileImageTimestamp")) {
                    profileImage1 = idCursor1.getString(idCursor1.getColumnIndexOrThrow("value"));
                }

            } while (idCursor1.moveToNext());
        }
        idCursor1.close();

        photoView = findViewById(R.id.imageView_patient);

        idView = findViewById(R.id.textView_ID);
        TextView dobView = findViewById(R.id.textView_DOB);
        TextView ageView = findViewById(R.id.textView_age);
        TextView addr1View = findViewById(R.id.textView_address_1);
        TableRow addr2Row = findViewById(R.id.tableRow_addr2);
        TextView addr2View = findViewById(R.id.textView_address2);
        TextView addrFinalView = findViewById(R.id.textView_address_final);
        TextView casteView = findViewById(R.id.textView_caste);
        TextView economic_statusView = findViewById(R.id.textView_economic_status);
        TextView education_statusView = findViewById(R.id.textView_education_status);
        TextView phoneView = findViewById(R.id.textView_phone);
        TextView sdwView = findViewById(R.id.textView_SDW);
        TableRow sdwRow = findViewById(R.id.tableRow_SDW);
        TextView occuView = findViewById(R.id.textView_occupation);
        TableRow occuRow = findViewById(R.id.tableRow_Occupation);
        TableRow economicRow = findViewById(R.id.tableRow_Economic_Status);
        TableRow educationRow = findViewById(R.id.tableRow_Education_Status);
        TableRow casteRow = findViewById(R.id.tableRow_Caste);

        TextView medHistView = findViewById(R.id.textView_patHist);
        TextView famHistView = findViewById(R.id.textView_famHist);

//changing patient to patient_new object
        if (patient_new.getMiddle_name() == null) {
            patientName = patient_new.getLast_name() + ", " + patient_new.getFirst_name();
        } else {
            patientName = patient_new.getLast_name() + ", " + patient_new.getFirst_name() + " " + patient_new.getMiddle_name();
        }
        setTitle(patientName);
        try {
            profileImage = imagesDAO.getPatientProfileChangeTime(patientUuid);
        } catch (DAOException e) {
            Crashlytics.getInstance().core.logException(e);
        }
        if (patient_new.getPatient_photo() == null || patient_new.getPatient_photo().equalsIgnoreCase("") || profileImage.equalsIgnoreCase(profileImage1)) {
            if (NetworkConnection.isOnline(getApplication())) {
                profilePicDownloaded();
            }
        }
        Glide.with(PatientDetailActivity.this)
                .load(patient_new.getPatient_photo())
                .thumbnail(0.3f)
                .centerCrop()
                .into(photoView);

        if (patient_new.getOpenmrs_id() != null && !patient_new.getOpenmrs_id().isEmpty()) {
            idView.setText(patient_new.getOpenmrs_id());
        } else {
            idView.setText(getString(R.string.patient_not_registered));
        }

        int age = DateAndTimeUtils.getAge(patient_new.getDate_of_birth());
        ageView.setText(String.valueOf(age));

        String dob = patient_new.getDate_of_birth();
        dobView.setText(dob);
        if (patient_new.getAddress1() == null || patient_new.getAddress1().equals("")) {
            addr1View.setVisibility(View.GONE);
        } else {
            addr1View.setText(patient_new.getAddress1());
        }
        if (patient_new.getAddress2() == null || patient_new.getAddress2().equals("")) {
            addr2Row.setVisibility(View.GONE);
        } else {
            addr2View.setText(patient_new.getAddress2());
        }
        String city_village;
        if (patient_new.getCity_village() != null) {
            city_village = patient_new.getCity_village().trim();
        } else {
            city_village = "";
        }

        String postal_code;
        if (patient_new.getPostal_code() != null) {
            postal_code = patient_new.getPostal_code().trim() + ",";
        } else {
            postal_code = "";
        }

        String addrFinalLine =
                String.format("%s, %s, %s %s",
                        city_village, patient_new.getState_province(),
                        postal_code, patient_new.getCountry());
        addrFinalView.setText(addrFinalLine);
        phoneView.setText(patient_new.getPhone_number());
        education_statusView.setText(patient_new.getEducation_level());
        economic_statusView.setText(patient_new.getEconomic_status());
        casteView.setText(patient_new.getCaste());
//
        if (patient_new.getSdw() != null && !patient_new.getSdw().equals("")) {
            sdwView.setText(patient_new.getSdw());
        } else {
            sdwRow.setVisibility(View.GONE);
        }
//
        if (patient_new.getOccupation() != null && !patient_new.getOccupation().equals("")) {
            occuView.setText(patient_new.getOccupation());
        } else {
            occuRow.setVisibility(View.GONE);
        }

        if (visitUuid != null && !visitUuid.isEmpty()) {
            CardView histCardView = findViewById(R.id.cardView_history);
            histCardView.setVisibility(View.GONE);
        } else {

            db = AppConstants.inteleHealthDatabaseHelper.getWritableDatabase();

            String visitIDSelection = "patientuuid = ?";
            String[] visitIDArgs = {patientUuid};
            Cursor visitIDCursor = db.query("tbl_visit", null, visitIDSelection, visitIDArgs, null, null, null);
            if (visitIDCursor != null && visitIDCursor.moveToFirst()) {
                visitUuid = visitIDCursor.getString(visitIDCursor.getColumnIndexOrThrow("uuid"));
            }
            visitIDCursor.close();
            db.close();

            db = AppConstants.inteleHealthDatabaseHelper.getWritableDatabase();
            EncounterDAO encounterDAO = new EncounterDAO();
            String encounterIDSelection = "visituuid = ?";
            String[] encounterIDArgs = {visitUuid};
            Cursor encounterCursor = db.query("tbl_encounter", null, encounterIDSelection, encounterIDArgs, null, null, null);
            if (encounterCursor != null && encounterCursor.moveToFirst()) {
                do {
                    if (encounterDAO.getEncounterTypeUuid("ENCOUNTER_VITALS").equalsIgnoreCase(encounterCursor.getString(encounterCursor.getColumnIndexOrThrow("encounter_type_uuid")))) {
                        encounterVitals = encounterCursor.getString(encounterCursor.getColumnIndexOrThrow("uuid"));
                    }
                    if (encounterDAO.getEncounterTypeUuid("ENCOUNTER_ADULTINITIAL").equalsIgnoreCase(encounterCursor.getString(encounterCursor.getColumnIndexOrThrow("encounter_type_uuid")))) {
                        encounterAdultIntials = encounterCursor.getString(encounterCursor.getColumnIndexOrThrow("uuid"));
                    }
                } while (encounterCursor.moveToNext());

            }
            encounterCursor.close();
            db.close();
            db = AppConstants.inteleHealthDatabaseHelper.getWritableDatabase();
            String medHistSelection = "encounteruuid = ? AND conceptuuid = ?";
            String[] medHistArgs = {encounterAdultIntials, UuidDictionary.RHK_MEDICAL_HISTORY_BLURB};
            String[] medHistColumms = {"value", " conceptuuid"};
            Cursor medHistCursor = db.query("tbl_obs", medHistColumms, medHistSelection, medHistArgs, null, null, null);
            medHistCursor.moveToLast();


            String medHistValue;

            try {
                medHistValue = medHistCursor.getString(medHistCursor.getColumnIndexOrThrow("value"));
            } catch (Exception e) {
                medHistValue = "";
            } finally {
                medHistCursor.close();
                db.close();
            }

            if (medHistValue != null && !medHistValue.equals("")) {
                medHistView.setText(Html.fromHtml(medHistValue));
            } else {
                medHistView.setText(getString(R.string.string_no_hist));
            }

            db = AppConstants.inteleHealthDatabaseHelper.getWritableDatabase();
            String famHistSelection = "encounteruuid = ? AND conceptuuid = ?";
            String[] famHistArgs = {encounterAdultIntials, UuidDictionary.RHK_FAMILY_HISTORY_BLURB};
            String[] famHistColumns = {"value", " conceptuuid"};
            Cursor famHistCursor = db.query("tbl_obs", famHistColumns, famHistSelection, famHistArgs, null, null, null);
            famHistCursor.moveToLast();
            String famHistValue;

            try {
                famHistValue = famHistCursor.getString(famHistCursor.getColumnIndexOrThrow("value"));
            } catch (Exception e) {
                famHistValue = "";
            } finally {
                famHistCursor.close();
                db.close();
            }

            if (famHistValue != null && !famHistValue.equals("")) {
                famHistView.setText(Html.fromHtml(famHistValue));
            } else {
                famHistView.setText(getString(R.string.string_no_hist));
            }
        }
        db = AppConstants.inteleHealthDatabaseHelper.getWritableDatabase();
        String visitSelection = "patientuuid = ?";
        String[] visitArgs = {dataString};
        String[] visitColumns = {"uuid, startdate", "enddate"};
        String visitOrderBy = "uuid";
        Cursor visitCursor = db.query("tbl_visit", visitColumns, visitSelection, visitArgs, null, null, visitOrderBy);

        previousVisitsList = findViewById(R.id.linearLayout_previous_visits);
        if (visitCursor.getCount() < 1) {
            neverSeen();
        } else {

            if (visitCursor.moveToLast() && visitCursor != null) {
                do {
                    String date = visitCursor.getString(visitCursor.getColumnIndexOrThrow("startdate"));
                    String end_date = visitCursor.getString(visitCursor.getColumnIndexOrThrow("enddate"));
                    String visit_id = visitCursor.getString(visitCursor.getColumnIndexOrThrow("uuid"));

                    String previsitSelection = "encounteruuid = ? AND conceptuuid = ?";
                    String[] previsitArgs = {encounterAdultIntials, UuidDictionary.CURRENT_COMPLAINT};
                    String[] previsitColumms = {"value", " conceptuuid", "encounteruuid"};
                    Cursor previsitCursor = db.query("tbl_obs", previsitColumms, previsitSelection, previsitArgs, null, null, null);
                    if (previsitCursor.moveToLast() && previsitCursor != null) {

                        String visitValue = previsitCursor.getString(previsitCursor.getColumnIndexOrThrow("value"));
                        if (visitValue != null && !visitValue.isEmpty()) {
                            String[] complaints = StringUtils.split(visitValue, Node.bullet_arrow);

                            visitValue = "";
                            String colon = ":";
                            if (complaints != null) {
                                for (String comp : complaints) {
                                    if (!comp.trim().isEmpty()) {
                                        visitValue = visitValue + Node.bullet_arrow + comp.substring(0, comp.indexOf(colon)) + "<br/>";
                                    }
                                }
                                if (!visitValue.isEmpty()) {
                                    visitValue = visitValue.substring(0, visitValue.length() - 2);
                                    visitValue = visitValue.replaceAll("<b>", "");
                                    visitValue = visitValue.replaceAll("</b>", "");
                                }
                                SimpleDateFormat currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                                try {

                                    Date formatted = currentDate.parse(date);
                                    String visitDate = currentDate.format(formatted);
                                    createOldVisit(visitDate, visit_id, end_date, visitValue);
                                } catch (ParseException e) {
                                    Crashlytics.getInstance().core.logException(e);
                                }
                            }
                        }
                        // Called when we select complaints but not select any sub knowledgeEngine inside that complaint
                        else {
                            SimpleDateFormat currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                            try {

                                Date formatted = currentDate.parse(date);
                                String visitDate = currentDate.format(formatted);
                                createOldVisit(visitDate, visit_id, end_date, visitValue);
                            } catch (ParseException e) {
                                Crashlytics.getInstance().core.logException(e);
                            }
                        }
                    }
                    // Called when we close app on vitals screen and Didn't select any complaints
                    else {
                        SimpleDateFormat currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        try {

                            Date formatted = currentDate.parse(date);
                            String visitDate = currentDate.format(formatted);
                            createOldVisit(visitDate, visit_id, end_date, visitValue);
                        } catch (ParseException e) {
                            Crashlytics.getInstance().core.logException(e);
                        }
                    }
                } while (visitCursor.moveToPrevious());
            }
        }
        visitCursor.close();
        db.close();

    }

    public void profilePicDownloaded() {
//        String url = "http://demo.intelehealth.io/openmrs/ws/rest/v1/personimage/" + patientUuid;
        UrlModifiers urlModifiers = new UrlModifiers();
        String url = urlModifiers.patientProfileImageUrl(patientUuid);
        Logger.logD(TAG, "profileimage url" + url);
        Observable<ResponseBody> profilePicDownload = AppConstants.apiInterface.PERSON_PROFILE_PIC_DOWNLOAD(url, "Basic " + sessionManager.getEncoded());
        profilePicDownload.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new DisposableObserver<ResponseBody>() {
                    @Override
                    public void onNext(ResponseBody file) {
                        DownloadFilesUtils downloadFilesUtils = new DownloadFilesUtils();
                        downloadFilesUtils.saveToDisk(file, patientUuid);
                        Logger.logD(TAG, file.toString());
                    }

                    @Override
                    public void onError(Throwable e) {
                        Logger.logD(TAG, e.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        Logger.logD(TAG, "complete" + patient_new.getPatient_photo());
                        PatientsDAO patientsDAO = new PatientsDAO();
                        boolean updated = false;
                        try {
                            updated = patientsDAO.updatePatientPhoto(patientUuid, IMAGE_PATH + patientUuid + ".jpg");
                        } catch (DAOException e) {
                            Crashlytics.getInstance().core.logException(e);
                        }
                        if (updated) {
                            Glide.with(PatientDetailActivity.this)
                                    .load(IMAGE_PATH + patientUuid + ".jpg")
                                    .thumbnail(0.3f)
                                    .centerCrop()
                                    .into(photoView);
                        }
                        ImagesDAO imagesDAO = new ImagesDAO();
                        boolean isImageDownloaded = false;
                        try {
                            isImageDownloaded = imagesDAO.insertPatientProfileImages(IMAGE_PATH + patientUuid + ".jpg", patientUuid);
                        } catch (DAOException e) {
                            Crashlytics.getInstance().core.logException(e);
                        }
                        if (isImageDownloaded)
                            AppConstants.notificationUtils.DownloadDone("Patient Image Download", "" + patient_new.getFirst_name() + "" + patient_new.getLast_name() + "'s Image Download complete.", getApplication());
                        else
                            AppConstants.notificationUtils.DownloadDone("Patient Image Download", "" + patient_new.getFirst_name() + "" + patient_new.getLast_name() + "'s Image Download Incomplete.", getApplication());


                    }
                });
    }

    /**
     * This method retrieves details about patient's old visits.
     *
     * @param datetime variable of type String.
     * @param visit_id variable of type int.
     * @return void
     */
    private void createOldVisit(final String datetime, String visit_id, String end_datetime, String visitValue) throws ParseException {
        // final LayoutInflater inflater = PatientDetailActivity.this.getLayoutInflater();
        //  View convertView = inflater.inflate(R.layout.list_item_previous_visit, null);
        //  TextView textView = (TextView) convertView.findViewById(R.id.textView_visit_info);

        final Boolean past_visit;
        final TextView textView = new TextView(this);

        final String visitString = String.format("Seen on (%s)", DateAndTimeUtils.SimpleDatetoLongDate(datetime));
        if (end_datetime == null || end_datetime.isEmpty()) {
            // visit has not yet ended

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            for (int i = 1; i <= 2; i++) {
                if (i == 1) {
                    SpannableString spannableString = new SpannableString(visitString + " Active");
                    Object greenSpan = new BackgroundColorSpan(Color.GREEN);
                    Object underlineSpan = new UnderlineSpan();
                    spannableString.setSpan(greenSpan, spannableString.length() - 6, spannableString.length(), 0);
                    spannableString.setSpan(underlineSpan, 0, spannableString.length() - 7, 0);
                    textView.setText(spannableString);
                    layoutParams.setMargins(2, 2, 2, 2);
                    previousVisitsList.addView(textView);
                }
                //If patient come up with any complaints
                if (i == 2) {
                    TextView complaintxt1 = new TextView(this);
                    complaintxt1.setLayoutParams(layoutParams);
                    if (visitValue != null && !visitValue.equals("")) {
                        complaintxt1.setText(Html.fromHtml(visitValue));
                    } else {
                        Log.e("Check", "No complaint");
                    }
                    layoutParams.setMargins(2, 2, 2, 2);
                    previousVisitsList.addView(complaintxt1);
                }
            }
            past_visit = false;

            if (newVisit.isEnabled()) {
                newVisit.setEnabled(false);
            }
            if (newVisit.isClickable()) {
                newVisit.setClickable(false);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    newVisit.setBackgroundColor
                            (getColor(R.color.divider));
                else
                    newVisit.setBackgroundColor(getResources().getColor(R.color.divider));
            }

        } else {
            // when visit has ended
            past_visit = true;
            for (int i = 1; i <= 2; i++) {
                if (i == 1) {
                    textView.setText(visitString);
                    textView.setPaintFlags(textView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    previousVisitsList.addView(textView);
                }
                //If patient has any past complaints
                if (i == 2) {
                    TextView complaintxt1 = new TextView(this);
                    if (visitValue != null && !visitValue.equals("")) {
                        complaintxt1.setText(Html.fromHtml(visitValue));
                    } else {
                        Log.e("Check", "No complaint");
                    }
                    previousVisitsList.addView(complaintxt1);
                }
            }
        }

        textView.setTextSize(18);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams
                (LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 0, 0, 0);
        textView.setLayoutParams(llp);
        textView.setTag(visit_id);

//        previousVisitsList.addView(textView);
       /* textView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();

                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        // Disallow ScrollView to intercept touch events.
                        Toast.makeText(PatientDetailActivity.this,"Touch Down",Toast.LENGTH_SHORT).show();
                        v.getParent().getParent().getParent()
                                .requestDisallowInterceptTouchEvent(true);
                        break;
                    case MotionEvent.ACTION_UP:
                        // Allow ScrollView to intercept touch events.
                        Toast.makeText(PatientDetailActivity.this,"Touch Up",Toast.LENGTH_SHORT).show();
                        v.getParent().getParent()
                                .requestDisallowInterceptTouchEvent(false);

                        break;
                }
                return true;
            }
        });*/
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // int position = (Integer) v.getTag();
                Intent visitSummary = new Intent(PatientDetailActivity.this, VisitSummaryActivity.class);
                visitSummary.putExtra("visitUuid", visitUuid);
                visitSummary.putExtra("patientUuid", patientUuid);
                visitSummary.putExtra("encounterUuidVitals", encounterVitals);
                visitSummary.putExtra("encounterUuidAdultIntial", encounterAdultIntials);
                visitSummary.putExtra("name", patientName);
                visitSummary.putExtra("tag", intentTag);
                visitSummary.putExtra("pastVisit", past_visit);
                startActivity(visitSummary);
            }
        });
        //previousVisitsList.addView(textView);
        //TODO: add on click listener to open the previous visit
    }

    /**
     * This method is called when patient has no prior visits.
     *
     * @return void
     */
    private void neverSeen() {
        final LayoutInflater inflater = PatientDetailActivity.this.getLayoutInflater();
        View convertView = inflater.inflate(R.layout.list_item_previous_visit, null);
        TextView textView = convertView.findViewById(R.id.textView_visit_info);
        String visitString = "No prior visits.";
        textView.setText(visitString);
        previousVisitsList.addView(convertView);
    }


    @Override
    public void onBackPressed() {
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    public class Myreceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                idView.setText(patientsDAO.getOpenmrsId(patientUuid));
            } catch (DAOException e) {
                Crashlytics.getInstance().core.logException(e);
            }
        }
    }
}
