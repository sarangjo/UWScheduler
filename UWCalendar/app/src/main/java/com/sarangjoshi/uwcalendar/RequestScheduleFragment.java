package com.sarangjoshi.uwcalendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.sarangjoshi.uwcalendar.data.FirebaseData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * TODO: add class comment
 *
 * @author Sarang Joshi
 */
public class RequestScheduleFragment extends DialogFragment {

    NameSelectedListener mListener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (NameSelectedListener) activity;
        } catch (ClassCastException e) {
            // TODO: Feq!
            throw new ClassCastException("Failed casting");
        }
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        FirebaseData fb = FirebaseData.getInstance();

        List<NameToId> objects = new ArrayList<>(fb.getAllUsers());

        Iterator<NameToId> iter = objects.iterator();
        while (iter.hasNext()) {
            NameToId curr = iter.next();
            if (curr.id.equals(fb.getUid())) {
                iter.remove();
                break;
            }
        }

        ArrayAdapter<NameToId> adapter = new ArrayAdapter<NameToId>(getActivity(),
                android.R.layout.select_dialog_singlechoice, objects) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                View v = inflater.inflate(android.R.layout.select_dialog_item, null);

                ((TextView) v.findViewById(android.R.id.text1)).setText(getItem(pos).name);

                return v;
            }
        };

        builder.setTitle("Request schedule.")
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    List<NameToId> objects;

                    public DialogInterface.OnClickListener init(List<NameToId> objects) {
                        this.objects = objects;
                        return this;
                    }

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(getActivity(), "Requesting: " + objects.get(which),
                                Toast.LENGTH_LONG).show();
                        mListener.nameSelected(objects.get(which));
                    }
                }.init(objects));
        return builder.create();
    }

    public interface NameSelectedListener {
        void nameSelected(NameToId selected);
    }

    public static class NameToId {
        public String name;
        public String id;

        public NameToId(String name, String id) {
            this.name = name;
            this.id = id;
        }

        public String toString() {
            return "Name " + name + ", id " + id;
        }
    }
}
