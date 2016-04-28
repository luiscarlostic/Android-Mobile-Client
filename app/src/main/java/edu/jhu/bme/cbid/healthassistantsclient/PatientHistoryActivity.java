package edu.jhu.bme.cbid.healthassistantsclient;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ExpandableListView;

import java.util.ArrayList;

import edu.jhu.bme.cbid.healthassistantsclient.objects.Node;

public class PatientHistoryActivity extends AppCompatActivity {

    Long patientID;
    ArrayList<String> physicalExams;

    int lastExpandedPosition = -1;

    String mFileName = "pathist.json";

    Node patientHistoryMap;
    NodeAdapter adapter;
    ExpandableListView historyListView;

    String patientHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

//        Bundle bundle = getIntent().getExtras();
//        patientID = bundle.getInt("patientID");
//        physicalExams = bundle.getStringArrayList("exams");

        setTitle(R.string.title_activity_patient_history);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_history);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(patientHistoryMap.anySubSelected()){
                    patientHistory = patientHistoryMap.generateLanguage();
                }

                Intent intent = new Intent(PatientHistoryActivity.this, FamilyHistoryActivity.class);
                intent.putExtra("patientID", patientID);
                intent.putStringArrayListExtra("exams", physicalExams);
                startActivity(intent);


            }
        });
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        patientHistoryMap = new Node(HelperMethods.encodeJSON(this, mFileName));
        historyListView = (ExpandableListView) findViewById(R.id.patient_history_expandable_list_view);
        adapter = new NodeAdapter(this, patientHistoryMap, this.getClass().getSimpleName());
        historyListView.setAdapter(adapter);

        historyListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                Node clickedNode = patientHistoryMap.getOption(groupPosition).getOption(childPosition);
                clickedNode.toggleSelected();
                if (patientHistoryMap.getOption(groupPosition).anySubSelected()) {
                    patientHistoryMap.getOption(groupPosition).setSelected();
                } else {
                    patientHistoryMap.getOption(groupPosition).setUnselected();
                }
                adapter.notifyDataSetChanged();

                if(!patientHistoryMap.getOption(groupPosition).getOption(childPosition).isTerminal()){
                    HelperMethods.subLevelQuestion(clickedNode, PatientHistoryActivity.this, adapter);
                }

                return false;
            }
        });

        historyListView.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {

                if (lastExpandedPosition != -1
                        && groupPosition != lastExpandedPosition) {
                    historyListView.collapseGroup(lastExpandedPosition);
                }
                lastExpandedPosition = groupPosition;
            }
        });

    }



}