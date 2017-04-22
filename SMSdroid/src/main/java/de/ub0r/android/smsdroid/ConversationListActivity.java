/*
 * Copyright (C) 2009-2015 Felix Bechstein
 * 
 * This file is part of SMSdroid.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.smsdroid;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.GridView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import de.ub0r.android.lib.DonationHelper;
import de.ub0r.android.lib.Utils;
import de.ub0r.android.lib.apis.Contact;
import de.ub0r.android.lib.apis.ContactsWrapper;
import de.ub0r.android.logg0r.Log;

/**
 * Main Activity showing conversations.
 *
 * @author flx
 */
public final class ConversationListActivity extends AppCompatActivity implements
        OnItemClickListener, OnItemLongClickListener {

    /**
     * Tag for output.
     */
    public static final String TAG = "main";

    /**
     * ORIG_URI to resolve.
     */
    static final Uri URI = Uri.parse("content://mms-sms/conversations/");

    /**
     * Number of items.
     */
    private static final int WHICH_N = 7;

    /**
     * Index in dialog: answer.
     */
    private static final int WHICH_ANSWER = 0;

    /**
     * Index in dialog: answer.
     */
    private static final int WHICH_CALL = 1;

    /**
     * Index in dialog: view/add contact.
     */
    private static final int WHICH_VIEW_CONTACT = 2;

    /**
     * Index in dialog: view.
     */
    private static final int WHICH_VIEW = 3;

    /**
     * Index in dialog: batch-select.
     */
    private static final int WHICH_BATCH_SELECT = 4;

    /**
     * Index in dialog: delete.
     */
    private static final int WHICH_DELETE = 5;

    /**
     * Index in dialog: mark as spam.
     */
    private static final int WHICH_MARK_SPAM = 6;

    /**
     * Number of items in the selection menu.
     */
    private static final int WHICH_SELECTION_N = 2;

    /**
     * Index in dialog: batch-select.
     */
    private static final int WHICH_SELECTION_CANCEL_SELECTION = 0;

    /**
     * Index in dialog: batch-select.
     */
    private static final int WHICH_SELECTION_DELETE_THREADS = 1;

    /**
     * Minimum date.
     */
    public static final long MIN_DATE = 10000000000L;

    /**
     * Miliseconds per seconds.
     */
    public static final long MILLIS = 1000L;

    /**
     * Show contact's photo.
     */
    public static boolean showContactPhoto = false;

    /**
     * Show emoticons in {@link MessageListActivity}.
     */
    public static boolean showEmoticons = false;

    /**
     * Dialog items shown if an item was long clicked.
     */
    private String[] longItemClickDialog = null;

    /**
     * Dialog items shown if an item was long clicked in selection mode.
     */
    private String[] longThreadSelectionClickDialog = null;

    /**
     * Handle conversation touches as though selecting them.
     */
    private boolean batchSelectionMode = false;

    /**
     * Are threads being deleted now?
     */
    private boolean batchSelectionBeingProcessed = false;

    /**
     * IDs of selected conversations (for a batch/bulk action).
     */
    private Set<Uri> selectedThreads = new HashSet<>();

    private static Handler threadDeletionHandler;

    /**
     * Conversations.
     */
    private ConversationAdapter adapter = null;

    /**
     * {@link Calendar} holding today 00:00.
     */
    private static final Calendar CAL_DAYAGO = Calendar.getInstance();

    static {
        // Get time for now - 24 hours
        CAL_DAYAGO.add(Calendar.DAY_OF_MONTH, -1);
    }

    private static final int PERMISSIONS_REQUEST_READ_SMS = 1;

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 2;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {
        super.onStart();
        AsyncHelper.setAdapter(adapter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        super.onStop();
        AsyncHelper.setAdapter(null);
    }

    /**
     * Get {@link AbsListView}.
     *
     * @return {@link AbsListView}
     */
    private AbsListView getListView() {
        return (AbsListView) findViewById(android.R.id.list);
    }

    /**
     * Set {@link ListAdapter} to {@link ListView}.
     *
     * @param la ListAdapter
     */
    private void setListAdapter(final ListAdapter la) {
        AbsListView v = getListView();
        if (v instanceof GridView) {
            //noinspection RedundantCast
            ((GridView) v).setAdapter(la);
        } else if (v instanceof ListView) {
            //noinspection RedundantCast
            ((ListView) v).setAdapter(la);
        }

    }

    /**
     * Show all rows of a particular {@link Uri}.
     *
     * @param context {@link Context}
     * @param u       {@link Uri}
     */
    @SuppressWarnings("UnusedDeclaration")
    static void showRows(final Context context, final Uri u) {
        Log.d(TAG, "-----GET HEADERS-----");
        Log.d(TAG, "-- ", u.toString(), " --");
        Cursor c = context.getContentResolver().query(u, null, null, null, null);
        if (c != null) {
            int l = c.getColumnCount();
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < l; i++) {
                buf.append(i).append(":");
                buf.append(c.getColumnName(i));
                buf.append(" | ");
            }
            Log.d(TAG, buf.toString());
        }

    }

    /**
     * Show rows for debugging purposes.
     *
     * @param context {@link Context}
     */
    static void showRows(@SuppressWarnings("UnusedParameters") final Context context) {
        // showRows(ContactsWrapper.getInstance().getUriFilter());
        // showRows(context, URI);
        // showRows(context, Uri.parse("content://sms/"));
        // showRows(context, Uri.parse("content://mms/"));
        // showRows(context, Uri.parse("content://mms/part/"));
        // showRows(context, ConversationProvider.CONTENT_URI);
        // showRows(context, Uri.parse("content://mms-sms/threads"));
        // showRows(Uri.parse(MessageList.URI));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNewIntent(final Intent intent) {
        if (intent != null) {
            Log.d(TAG, "got intent: ", intent.getAction());
            Log.d(TAG, "got uri: ", intent.getData());
            final Bundle b = intent.getExtras();
            if (b != null) {
                Log.d(TAG, "user_query: ", b.get("user_query"));
                Log.d(TAG, "got extra: ", b);
            }
            final String query = intent.getStringExtra("user_query");
            Log.d(TAG, "user query: ", query);
            // TODO: do something with search query
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        final Intent i = getIntent();
        Log.d(TAG, "got intent: ", i.getAction());
        Log.d(TAG, "got uri: ", i.getData());
        Log.d(TAG, "got extra: ", i.getExtras());

        setTheme(PreferencesActivity.getTheme(this));
        Utils.setLocale(this);
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("use_gridlayout", false)) {
            setContentView(R.layout.conversationgrid);
        } else {
            setContentView(R.layout.conversationlist);
        }

        if (DonationHelper.hideAds(this)) {
            findViewById(R.id.cookie_consent).setVisibility(View.GONE);
        }

        // debug info
        showRows(this);

        final AbsListView list = getListView();
        list.setOnItemClickListener(this);
        list.setOnItemLongClickListener(this);
        longItemClickDialog = new String[WHICH_N];
        longItemClickDialog[WHICH_ANSWER] = getString(R.string.reply);
        longItemClickDialog[WHICH_CALL] = getString(R.string.call);
        longItemClickDialog[WHICH_VIEW_CONTACT] = getString(R.string.view_contact_);
        longItemClickDialog[WHICH_VIEW] = getString(R.string.view_thread_);
        longItemClickDialog[WHICH_BATCH_SELECT] = getString(R.string.batch_select_);
        longItemClickDialog[WHICH_DELETE] = getString(R.string.delete_thread_);
        longItemClickDialog[WHICH_MARK_SPAM] = getString(R.string.filter_spam_);

        longThreadSelectionClickDialog = new String[WHICH_SELECTION_N];
        longThreadSelectionClickDialog[WHICH_SELECTION_CANCEL_SELECTION] = getString(R.string.cancel_thread_selection);
        longThreadSelectionClickDialog[WHICH_SELECTION_DELETE_THREADS] = getString(R.string.delete_selected_threads_);

        initAdapter();

        if (!SMSdroid.isDefaultApp(this)) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle(R.string.not_default_app);
            b.setMessage(R.string.not_default_app_message);
            b.setNegativeButton(android.R.string.cancel, null);
            b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @TargetApi(Build.VERSION_CODES.KITKAT)
                @Override
                public void onClick(final DialogInterface dialogInterface, final int i) {
                    Intent intent =
                            new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                            BuildConfig.APPLICATION_ID);
                    startActivity(intent);
                }
            });
            b.show();
        }
    }

    private void initAdapter() {
        if (!SMSdroid.requestPermission(this, Manifest.permission.READ_SMS,
                PERMISSIONS_REQUEST_READ_SMS, R.string.permissions_read_sms,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                })) {
            return;
        }

        if (!SMSdroid.requestPermission(this, Manifest.permission.READ_CONTACTS,
                PERMISSIONS_REQUEST_READ_CONTACTS, R.string.permissions_read_contacts,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                })) {
            return;
        }

        adapter = new ConversationAdapter(this);
        setListAdapter(adapter);
        adapter.startMsgListQuery();
    }

    @Override
    protected void onResume() {
        super.onResume();
        CAL_DAYAGO.setTimeInMillis(System.currentTimeMillis());
        CAL_DAYAGO.add(Calendar.DAY_OF_MONTH, -1);

        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        showContactPhoto = p.getBoolean(PreferencesActivity.PREFS_CONTACT_PHOTO, true);
        showEmoticons = p.getBoolean(PreferencesActivity.PREFS_EMOTICONS, false);
        if (adapter != null) {
            adapter.startMsgListQuery();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.conversationlist, menu);
        if (DonationHelper.hideAds(this)) {
            menu.removeItem(R.id.item_donate);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        final SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hideDeleteAll = p
                .getBoolean(PreferencesActivity.PREFS_HIDE_DELETE_ALL_THREADS, false);
        menu.findItem(R.id.item_delete_all_threads).setVisible(!hideDeleteAll);

        if (! batchSelectionMode) {
            menu.findItem(R.id.item_cancel_batch_select).setVisible(false);
            menu.findItem(R.id.item_delete_selected_threads).setVisible(false);

            menu.findItem(R.id.item_compose).setVisible(true);
            menu.findItem(R.id.item_batch_select).setVisible(true);
            menu.findItem(R.id.item_mark_all_read).setVisible(true);
            menu.findItem(R.id.item_delete_all_threads).setVisible(!hideDeleteAll);
            menu.findItem(R.id.item_settings).setVisible(true);
        }
        else {
            menu.findItem(R.id.item_cancel_batch_select).setVisible(true);
            menu.findItem(R.id.item_delete_selected_threads).setVisible(true);

            menu.findItem(R.id.item_compose).setVisible(false);
            menu.findItem(R.id.item_batch_select).setVisible(false);
            menu.findItem(R.id.item_mark_all_read).setVisible(false);
            menu.findItem(R.id.item_delete_all_threads).setVisible(false);
            menu.findItem(R.id.item_settings).setVisible(false);
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            final int requestCode,
            @NonNull final String permissions[],
            @NonNull final int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_SMS: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // just try again.
                    initAdapter();
                } else {
                    // this app is useless without permission for reading sms
                    Log.e(TAG, "permission for reading sms denied, exit");
                    finish();
                }
                return;
            }
            case PERMISSIONS_REQUEST_READ_CONTACTS: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // just try again.
                    initAdapter();
                } else {
                    // this app is useless without permission for reading sms
                    Log.e(TAG, "permission for reading contacts denied, exit");
                    finish();
                }
                return;
            }
        }
    }

    private void stopThreadDeletion ()
    {
        if (threadDeletionHandler != null) {
            threadDeletionHandler.removeCallbacksAndMessages(null);
            threadDeletionHandler = null;
        }

        batchSelectionBeingProcessed = false;
    }

    private void setBatchSelectionModeOn() {
        Log.d(TAG, "setBatchSelectionModeOn");

        // Stop the previous process.
        stopThreadDeletion();
        selectedThreads.clear();

        batchSelectionMode = true;
        // TODO Some visual indication that the mode is on (apart from the selected threads,
        //      as no threads may be selected, and still the mode is on).
        //      (https://github.com/JanisE/smsdroid/issues/3)
    }

    private void setBatchSelectionModeOff() {
        Log.d(TAG, "setBatchSelectionModeOff");

        batchSelectionMode = false;

        if (! batchSelectionBeingProcessed) {
            // TODO Update the view (see removeThreadFromSelection). (https://github.com/JanisE/smsdroid/issues/4)
            selectedThreads.clear();
        }
        // Else: "selectedThreads" are managed by the deletion process.
    }

    public boolean isThreadSelected(Uri threadUri)
    {
        return selectedThreads.contains(threadUri);
    }

    private void toggleThreadSelection(Uri threadUri, final View view) {
        Log.d(TAG, "toggleThreadSelection(threadUri): ", threadUri);

        if (batchSelectionMode) {
            if (! isThreadSelected(threadUri)) {
                addThreadToSelection(threadUri, view);
            }
            else {
                removeThreadFromSelection(threadUri, view);
            }
        }
        else {
            Log.e(TAG, "toggleThreadSelection called in ! batchSelectionMode mode! Thread: ", threadUri);
        }
    }

    private void addThreadToSelection(Uri threadUri, final View view) {
        Log.d(TAG, "addThreadToSelection(threadUri): ", threadUri);

        if (batchSelectionMode) {
            selectedThreads.add(threadUri);
            ((ConversationAdapter.ViewHolder) view.getTag()).MarkConversationSelected();
        }
        else {
            Log.e(TAG, "addThreadToSelection called in ! batchSelectionMode mode! Thread: ", threadUri);
        }
    }

    private void removeThreadFromSelection(Uri threadUri, final View view) {
        Log.d(TAG, "removeThreadFromSelection(threadUri): ", threadUri);

        if (batchSelectionMode) {
            selectedThreads.remove(threadUri);
            ((ConversationAdapter.ViewHolder) view.getTag()).MarkConversationUnselected();
        }
        else {
            Log.e(TAG, "addThreadToSelection called in ! batchSelectionMode mode! Thread: ", threadUri);
        }
    }

    /**
     * Mark all messages with a given {@link Uri} as read.
     *
     * @param context {@link Context}
     * @param uri     {@link Uri}
     * @param read    read status
     */
    static void markRead(final Context context, final Uri uri, final int read) {
        Log.d(TAG, "markRead(", uri, ",", read, ")");
        if (uri == null) {
            return;
        }
        String[] sel = Message.SELECTION_UNREAD;
        if (read == 0) {
            sel = Message.SELECTION_READ;
        }
        final ContentResolver cr = context.getContentResolver();
        final ContentValues cv = new ContentValues();
        cv.put(Message.PROJECTION[Message.INDEX_READ], read);
        try {
            cr.update(uri, cv, Message.SELECTION_READ_UNREAD, sel);
        } catch (IllegalArgumentException | SQLiteException e) {
            Log.e(TAG, "failed update", e);
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
        SmsReceiver.updateNewMessageNotification(context, null);
    }

    /**
     * Delete messages with a given {@link Uri}.
     *
     * @param context  {@link Context}
     * @param uri      {@link Uri}
     * @param title    title of Dialog
     * @param message  message of the Dialog
     * @param activity {@link Activity} to finish when deleting.
     */
    static void deleteMessages(final Context context, final Uri uri, final int title,
                               final int message, final Activity activity) {
        Log.i(TAG, "deleteMessages(..,", uri, " ,..)");
        final Builder builder = new Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setNegativeButton(android.R.string.no, null);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                try {
                    final int ret = context.getContentResolver().delete(uri, null, null);
                    Log.d(TAG, "deleted: ", ret);
                    if (activity != null && !activity.isFinishing()) {
                        activity.finish();
                    }
                    if (ret > 0) {
                        Conversation.flushCache();
                        Message.flushCache();
                        SmsReceiver.updateNewMessageNotification(context, null);
                    }
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Argument Error", e);
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_LONG).show();
                } catch (SQLiteException e) {
                    Log.e(TAG, "SQL Error", e);
                    Toast.makeText(context, R.string.error_unknown, Toast.LENGTH_LONG).show();
                }

            }
        });
        builder.show();
    }

//    private String getCommonContentUriPart (Uri uri)
//    {
//        return uri.toString().substring(0, uri.toString().length() - uri.getLastPathSegment().length());
//    }

    /**
     * Delete threads selected previously.
     */
    public void deleteSelectedThreads ()
    {
        Log.i(TAG, "deleteSelectedThreads(): count of selected threads = ", selectedThreads.size(), ".");
        final Builder builder = new Builder(this);
        builder.setTitle(R.string.delete_selected_threads_);
        builder.setMessage(R.string.delete_selected_threads_question);
        builder.setNegativeButton(android.R.string.no, null);

        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                batchSelectionBeingProcessed = true;

                Log.d(TAG, "deleteSelectedThreads(): ", selectedThreads.size(), " threads.");

                threadDeletionHandler = new Handler();
                threadDeletionHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Deleting next thread...");

                        int threadCount = selectedThreads.size();

                        if (threadCount > 0) {
                            Uri threadUri = selectedThreads.iterator().next();

                            try {
                                Log.d(TAG, "Deleting: " + threadUri.toString());
                                final int ret = getContentResolver().delete(threadUri, null, null);
                                Log.d(TAG, "deleted: ", ret);

                            } catch (IllegalArgumentException e) {
                                Log.e(TAG, "Argument Error", e);
                                Toast.makeText(ConversationListActivity.this, R.string.error_unknown, Toast.LENGTH_LONG).show();
                            } catch (SQLiteException e) {
                                Log.e(TAG, "SQL Error", e);
                                Toast.makeText(ConversationListActivity.this, R.string.error_unknown, Toast.LENGTH_LONG).show();
                            }

                            selectedThreads.remove(threadUri);
                            threadDeletionHandler.postDelayed(this, threadCount - 1 > 0 ? 40000 : 0);
                        }
                        else {
                            Log.i(TAG, "All selected threads processed.");

                            stopThreadDeletion();

                            Conversation.flushCache();
                            Message.flushCache();
                            SmsReceiver.updateNewMessageNotification(ConversationListActivity.this, null);
                        }
                    }
                }, 0);

                // FIXME The stuff below does not work. (https://github.com/JanisE/smsdroid/issues/2)
//                if (selectedThreads.size() > 0) {
//                    String contentUri = getCommonContentUriPart(selectedThreads.iterator().next());
//                    String threadIdCondition = "";
//                    String separator = "";
//                    for (Uri threadUri : selectedThreads) {
//                        try {
//                            if (contentUri.equals(getCommonContentUriPart(threadUri))) {
//                                int threadId = Integer.parseInt(threadUri.getLastPathSegment());
//                                threadIdCondition += separator + threadId;
//                                separator = ", ";   // Add a comma before all subsequent (just not the first) parts.
//                            }
//                            else {
//                                // Collect only threads with the same common URI part.
//                                //      TODO Collect all selected threads in separate sets and delete each one separately.
//                                //      Workaround: After the first deletion one type of threads are deleted.
//                                //          In the second attempt the other type(s) may be deleted.
//                                Log.i(TAG, "Ignored thread with another URI part: " + getCommonContentUriPart(threadUri));
//                            }
//                        } catch (NumberFormatException e) {
//                            Log.e(TAG, "unable to parse thread id from uri: ", threadUri, e);
//                        }
//                    }
//
//                    if (! threadIdCondition.isEmpty()) {
//                        try {
//                            threadIdCondition = Telephony.Sms.THREAD_ID + " IN (" + threadIdCondition + ")";
//                            Log.d(TAG, "Deleting " + contentUri + " WHERE " + threadIdCondition);
//                            final int ret = getContentResolver().delete(Uri.parse(contentUri), Telephony.Sms.THREAD_ID + " IN (" + threadIdCondition + ")", null);
//                            Log.d(TAG, "deleted: ", ret);
//
//                            if (ret > 0) {
//                                Conversation.flushCache();
//                                Message.flushCache();
//                                SmsReceiver.updateNewMessageNotification(ConversationListActivity.this, null);
//                            }
//                        } catch (IllegalArgumentException e) {
//                            Log.e(TAG, "Argument Error", e);
//                            Toast.makeText(ConversationListActivity.this, R.string.error_unknown, Toast.LENGTH_LONG).show();
//                        } catch (SQLiteException e) {
//                            Log.e(TAG, "SQL Error", e);
//                            Toast.makeText(ConversationListActivity.this, R.string.error_unknown, Toast.LENGTH_LONG).show();
//                        }
//                    }
//                    else {
//                        Log.e(TAG, "Nothing to delete, which is unexpected at this point.");
//                    }
//                }
//                // Else: Nothing was selected, nothing actually to do.

                setBatchSelectionModeOff();

//                Conversation.flushCache();
//                Message.flushCache();
//                SmsReceiver.updateNewMessageNotification(ConversationListActivity.this, null);
            }
        });
        builder.show();
    }

    /**
     * Add or remove an entry to/from blacklist.
     *
     * @param context {@link Context}
     * @param addr    address
     */
    private static void addToOrRemoveFromSpamlist(final Context context, final String addr) {
        SpamDB.toggleBlacklist(context, addr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.item_compose:
                final Intent i = getComposeIntent(this, null, false);
                try {
                    startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "error launching intent: ", i.getAction(), ", ", i.getData());
                    Toast.makeText(this,
                            "error launching messaging app!\nPlease contact the developer.",
                            Toast.LENGTH_LONG).show();
                }
                return true;
            case R.id.item_settings: // start settings activity
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    startActivity(new Intent(this, Preferences11Activity.class));
                } else {
                    startActivity(new Intent(this, PreferencesActivity.class));
                }
                return true;
            case R.id.item_donate:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, DonationHelper.DONATOR_URI));
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "error opening play store with donation app", e);
                }
                return true;
            case R.id.item_delete_all_threads:
                deleteMessages(this, Uri.parse("content://sms/"), R.string.delete_threads_,
                        R.string.delete_threads_question, null);
                return true;
            case R.id.item_mark_all_read:
                markRead(this, Uri.parse("content://sms/"), 1);
                markRead(this, Uri.parse("content://mms/"), 1);
                return true;
            case R.id.item_batch_select:
                setBatchSelectionModeOn();
                // Return to the conversation list.
                return true;
            case R.id.item_cancel_batch_select:
                setBatchSelectionModeOff();
                // Return to the conversation list.
                return true;
            case R.id.item_delete_selected_threads:
                deleteSelectedThreads();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Get a {@link Intent} for sending a new message.
     *
     * @param context     {@link Context}
     * @param address     address
     * @param showChooser show chooser
     * @return {@link Intent}
     */
    static Intent getComposeIntent(final Context context, final String address,
                                   final boolean showChooser) {
        Intent i = null;

        if (!showChooser && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Search for WebSMS
            PackageManager pm = context.getPackageManager();
            i = pm == null ? null : pm.getLaunchIntentForPackage("de.ub0r.android.websms");
        }

        if (i == null) {
            Log.d(TAG, "WebSMS is not installed!");
            i = new Intent(Intent.ACTION_SENDTO);
        }

        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (address == null) {
            i.setData(Uri.parse("sms:"));
        } else {
            i.setData(Uri.parse("smsto:" + PreferencesActivity.fixNumber(context, address)));
        }

        return i;
    }

    /**
     * {@inheritDoc}
     */
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        final Conversation c = Conversation.getConversation(this,
                (Cursor) parent.getItemAtPosition(position), false);
        final Uri target = c.getUri();

        if (batchSelectionMode) {
            toggleThreadSelection(target, view);
        }
        else {
            final Intent i = new Intent(this, MessageListActivity.class);
            i.setData(target);
            try {
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "error launching intent: ", i.getAction(), ", ", i.getData());
                Toast.makeText(this,
                        "error launching messaging app!\n" + "Please contact the developer.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean onItemLongClick(final AdapterView<?> parent, final View view,
                                   final int position, final long id) {
        final Conversation c = Conversation.getConversation(this,
                (Cursor) parent.getItemAtPosition(position), true);
        final Uri target = c.getUri();
        if (ContentUris.parseId(target) < 0) {
            // do not show anything for broken threadIds
            return true;
        }
        Builder builder = new Builder(this);

        if (! batchSelectionMode) {
            String[] items = longItemClickDialog;
            final Contact contact = c.getContact();
            final String a = contact.getNumber();
            Log.d(TAG, "p: ", a);
            final String n = contact.getName();
            if (TextUtils.isEmpty(n)) {
                builder.setTitle(a);
                items = items.clone();
                items[WHICH_VIEW_CONTACT] = getString(R.string.add_contact_);
            } else {
                builder.setTitle(n);
            }
            if (SpamDB.isBlacklisted(getApplicationContext(), a)) {
                items = items.clone();
                items[WHICH_MARK_SPAM] = getString(R.string.dont_filter_spam_);
            }
            builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    Intent i;
                    try {
                        switch (which) {
                            case WHICH_ANSWER:
                                ConversationListActivity.this.startActivity(getComposeIntent(
                                        ConversationListActivity.this, a, false));
                                break;
                            case WHICH_CALL:
                                i = new Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + a));
                                ConversationListActivity.this.startActivity(i);
                                break;
                            case WHICH_VIEW_CONTACT:
                                if (n == null) {
                                    i = ContactsWrapper.getInstance().getInsertPickIntent(a);
                                    Conversation.flushCache();
                                } else {
                                    final Uri uri = c.getContact().getUri();
                                    i = new Intent(Intent.ACTION_VIEW, uri);
                                }
                                ConversationListActivity.this.startActivity(i);
                                break;
                            case WHICH_VIEW:
                                i = new Intent(ConversationListActivity.this,
                                        MessageListActivity.class);
                                i.setData(target);
                                ConversationListActivity.this.startActivity(i);
                                break;
                            case WHICH_BATCH_SELECT:
                                setBatchSelectionModeOn();
                                addThreadToSelection(target, view);
                                break;
                            case WHICH_DELETE:
                                ConversationListActivity
                                        .deleteMessages(ConversationListActivity.this, target,
                                                R.string.delete_thread_,
                                                R.string.delete_thread_question,
                                                null);
                                break;
                            case WHICH_MARK_SPAM:
                                ConversationListActivity.addToOrRemoveFromSpamlist(
                                        ConversationListActivity.this, c.getContact().getNumber());
                                break;
                            default:
                                break;
                        }
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "unable to launch activity:", e);
                        Toast.makeText(ConversationListActivity.this, R.string.error_unknown,
                                Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
        else {
            String[] items = longThreadSelectionClickDialog;

            builder.setItems(longThreadSelectionClickDialog, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, final int which) {
                    switch (which) {
                        case WHICH_SELECTION_CANCEL_SELECTION:
                            setBatchSelectionModeOff();
                            break;
                        case WHICH_SELECTION_DELETE_THREADS:
                            deleteSelectedThreads();
                            break;
                        default:
                            break;
                    }
                }
            });
        }
        builder.create().show();
        return true;
    }

    /**
     * Convert time into formated date.
     *
     * @param context {@link Context}
     * @param time    time
     * @return formated date.
     */
    static String getDate(final Context context, final long time) {
        long t = time;
        if (t < MIN_DATE) {
            t *= MILLIS;
        }
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                PreferencesActivity.PREFS_FULL_DATE, false)) {
            return DateFormat.getTimeFormat(context).format(t) + " "
                    + DateFormat.getDateFormat(context).format(t);
        } else if (t < CAL_DAYAGO.getTimeInMillis()) {
            return DateFormat.getDateFormat(context).format(t);
        } else {
            return DateFormat.getTimeFormat(context).format(t);
        }
    }
}
