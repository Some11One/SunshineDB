/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.example.android.sunshine.app.data.WeatherContract;

import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates fetching the forecast and displaying it as a {@link ListView} layout.
 */
public class ForecastFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {

    // log
    private final String LOG_TAG = ForecastFragment.class.getSimpleName();

    // saved cities key for sharedPreferences
    private final static String SAVED_CITIES_KEY = "SAVED_CITIES";

    private static final int FORECAST_LOADER = 0;

    // For the forecast view we're showing only a small subset of the stored data.
    // Specify the columns we need.
    private static final String[] FORECAST_COLUMNS = {
            // In this case the id needs to be fully qualified with a table name, since
            // the content provider joins the location & weather tables in the background
            // (both have an _id column)
            // On the one hand, that's annoying.  On the other, you can search the weather table
            // using the location set by the user, which is only in the Location table.
            // So the convenience is worth it.
            WeatherContract.WeatherEntry.TABLE_NAME + "." + WeatherContract.WeatherEntry._ID,
            WeatherContract.WeatherEntry.COLUMN_DATE,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.LocationEntry.COLUMN_LOCATION_SETTING,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.LocationEntry.COLUMN_COORD_LAT,
            WeatherContract.LocationEntry.COLUMN_COORD_LONG
    };

    // These indices are tied to FORECAST_COLUMNS.  If FORECAST_COLUMNS changes, these
    // must change.
    static final int COL_WEATHER_ID = 0;
    static final int COL_WEATHER_DATE = 1;
    static final int COL_WEATHER_DESC = 2;
    static final int COL_WEATHER_MAX_TEMP = 3;
    static final int COL_WEATHER_MIN_TEMP = 4;
    static final int COL_LOCATION_SETTING = 5;
    static final int COL_WEATHER_CONDITION_ID = 6;
    static final int COL_COORD_LAT = 7;
    static final int COL_COORD_LONG = 8;

    private ForecastAdapter mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            getLoaderManager().restartLoader(FORECAST_LOADER, null, this);
            return true;
        }
        if (id == R.id.action_choose_city){
            chooseCity();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mForecastAdapter = new ForecastAdapter(getActivity(), null, 0);
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Get a reference to the ListView, and attach this adapter to it.
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);

        // We'll call our MainActivity
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                // CursorAdapter returns a cursor at the correct position for getItem(), or null
                // if it cannot seek to that position.
                Cursor cursor = (Cursor) adapterView.getItemAtPosition(position);
                if (cursor != null) {
                    String locationSetting = Utility.getPreferredLocation(getActivity());
                    Intent intent = new Intent(getActivity(), DetailActivity.class)
                            .setData(WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                                    locationSetting, cursor.getLong(COL_WEATHER_DATE)
                            ));
                    startActivity(intent);
                }
            }
        });
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        getLoaderManager().initLoader(FORECAST_LOADER, null, this);
        super.onActivityCreated(savedInstanceState);
    }

    // since we read the location when we create the loader, all we need to do is restart things
    void onLocationChanged() {
        updateWeather();
        getLoaderManager().restartLoader(FORECAST_LOADER, null, this);
    }

    private void updateWeather(String weatherPostalCode){
        FetchWeatherTask weatherTask = new FetchWeatherTask(getActivity());
        weatherTask.execute(weatherPostalCode);
    }

    private void updateWeather() {
        FetchWeatherTask weatherTask = new FetchWeatherTask(getActivity());

        // load shared Preferences
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        // checking current postal code preference
        final String weatherPostalCode = sharedPreferences.getString(getActivity().getString(R.string.pref_location_key)
                , getActivity().getString(R.string.pref_location_default));

        // check if user wants to save city
        final Set<String> savedCity = sharedPreferences.getStringSet(SAVED_CITIES_KEY,  new HashSet<String>());
        if (!savedCity.contains(weatherPostalCode)){
            // ask to add city
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage("Save location: " + getCity(weatherPostalCode) + "?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Set<String> newSavedCities = new HashSet<>(savedCity.size() + 1);
                            newSavedCities.addAll(savedCity);
                            newSavedCities.add(weatherPostalCode);
                            sharedPreferences.edit().putStringSet(SAVED_CITIES_KEY, newSavedCities).apply();
                        }
                    })
                    .setNegativeButton("No", new DialogInterface.OnClickListener(){
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        }


        weatherTask.execute(weatherPostalCode);
    }

    // let user decide witch city weather to show
    private void chooseCity(){

        // get saved cities
        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final Set<String> savedCity = sharedPreferences.getStringSet(SAVED_CITIES_KEY,  new HashSet<String>());

        // no saved cities
        if (savedCity.size() == 0){
            Toast.makeText(getActivity(), "No saved cities", Toast.LENGTH_SHORT).show();
            return;
        }

        // prepare dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        CharSequence[] charSequences = new CharSequence[savedCity.size()];
        int index = 0;
        for (String f : savedCity) {
            charSequences[index] = getCity(f);
            index++;
        }
        builder.setTitle("Choose city")
                .setItems(charSequences, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String newCity = (String) savedCity.toArray()[which];
//                        FetchWeatherTask weatherTask = new FetchWeatherTask(getActivity());
//                        weatherTask.execute(newCity);
                       //  updateWeather(newCity);
                        // getLoaderManager().restartLoader(FORECAST_LOADER, null, ForecastFragment.this);
                        sharedPreferences.edit().putString(getActivity().getString(R.string.pref_location_key)
                                , newCity).apply();
                        // updateWeather(newCity);
                        onLocationChanged();
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // get City name from postal code
    public static String getCity(String postal){
        switch (postal){
            case "143320": return Cities.MOSCOW.getName();
            case "198504": return Cities.SAINT_PETERSBURG.getName();
            case "141070": return Cities.KOROLEV.getName();
            default:
                return "";
        }
    }

    // cities and their postal code
    enum Cities{
        MOSCOW{
            public String getPostal(){
                return "143320";
            }
            public String getName(){
                return "Moscow";
            }
        }, SAINT_PETERSBURG{
            public String getPostal(){
                return "198504";
            }
            public String getName(){
                return "Saint Petersburg";
            }
        }, KOROLEV{
            public String getPostal(){
                return "141070";
            }
            public String getName(){
                return "Korolev";
            }
        };

        public abstract String getPostal();
        public abstract String getName();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        String locationSetting = Utility.getPreferredLocation(getActivity());

        // Sort order:  Ascending, by date.
        String sortOrder = WeatherContract.WeatherEntry.COLUMN_DATE + " ASC";
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                locationSetting, System.currentTimeMillis());

        return new CursorLoader(getActivity(),
                weatherForLocationUri,
                FORECAST_COLUMNS,
                null,
                null,
                sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mForecastAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mForecastAdapter.swapCursor(null);
    }
}
