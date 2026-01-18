package com.uvarara.quiz.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.uvarara.quiz.Constant;
import com.uvarara.quiz.R;
import com.uvarara.quiz.helper.SettingsPreferences;
import com.uvarara.quiz.model.Bookmark;

import java.util.ArrayList;
import java.util.Locale;

public class BookmarkList extends AppCompatActivity {

    private static final String TAG = "BookmarkList";

    RecyclerView recyclerView;
    RecyclerView.LayoutManager layoutManager;
    ImageView back, setting;
    TextView title, tvNoBookmarked;
    ArrayList<Bookmark> bookmarks;
    BookMarkAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark_list);

        back = findViewById(R.id.back);
        setting = findViewById(R.id.setting);
        title = findViewById(R.id.tvLevel);
        tvNoBookmarked = findViewById(R.id.emptyMsg);
        title.setText(R.string.bookmark_list_title); // crea una stringa in resources se non esiste

        recyclerView = findViewById(R.id.recyclerView);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        bookmarks = MainActivity.bookmarkDBHelper.getAllBookmarkedList();
        Log.d(TAG, "Bookmarks: " + bookmarks);

        updateEmptyState();

        adapter = new BookMarkAdapter(bookmarks);
        recyclerView.setAdapter(adapter);

        back.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        setting.setOnClickListener(v -> {
            if (SettingsPreferences.getSoundEnableDisable(this)) {
                Constant.backSoundonclick(this);
            }
            if (SettingsPreferences.getVibration(this)) {
                Constant.vibrate(this, Constant.VIBRATION_DURATION);
            }
            Intent settingsIntent = new Intent(this, SettingActivity.class);
            startActivity(settingsIntent);
            overridePendingTransition(R.anim.open_next, R.anim.close_next);
        });
    }

    private void updateEmptyState() {
        tvNoBookmarked.setVisibility((bookmarks == null || bookmarks.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    public class BookMarkAdapter extends RecyclerView.Adapter<BookMarkAdapter.ItemRowHolder> {
        private final ArrayList<Bookmark> bookmarks;

        public BookMarkAdapter(ArrayList<Bookmark> bookmarks) {
            this.bookmarks = bookmarks;
        }

        @NonNull
        @Override
        public ItemRowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.bookmark_layout, parent, false);
            return new ItemRowHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ItemRowHolder holder, int position) {
            Bookmark bookmark = bookmarks.get(position);
            holder.tvNo.setText(String.format(Locale.getDefault(), "%d.", position + 1));
            holder.tvQue.setText(bookmark.getQuestion());
            holder.tvAns.setText(bookmark.getAnswer());

            holder.remove.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;

                MainActivity.bookmarkDBHelper.delete_id(bookmark.getQue_id());
                bookmarks.remove(pos);
                notifyItemRemoved(pos);
                // Aggiorna numerazione rimanente
                notifyItemRangeChanged(pos, getItemCount() - pos);
                updateEmptyState();
            });

            holder.cardView.setOnClickListener(v -> SolutionDialog(bookmark.getQuestion(), bookmark.getSolution()));
        }

        @Override
        public int getItemCount() {
            return (bookmarks != null) ? bookmarks.size() : 0;
        }

        public class ItemRowHolder extends RecyclerView.ViewHolder {
            TextView tvNo, tvQue, tvAns;
            ImageView remove;
            CardView cardView;

            public ItemRowHolder(@NonNull View itemView) {
                super(itemView);
                tvNo = itemView.findViewById(R.id.tvNo);
                tvQue = itemView.findViewById(R.id.tvQue);
                tvAns = itemView.findViewById(R.id.tvAns);
                remove = itemView.findViewById(R.id.remove);
                cardView = itemView.findViewById(R.id.cardView);
            }
        }
    }

    public void SolutionDialog(String question, String solution) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.note_dialog_layout, null);
        builder.setView(dialogView);

        TextView tvQuestion = dialogView.findViewById(R.id.question);
        TextView tvSolution = dialogView.findViewById(R.id.solution);

        Log.d(TAG, "Solution for: " + question + " -> " + solution);

        tvQuestion.setText(question);
        tvSolution.setText(solution);

        AlertDialog alertDialog = builder.create();
        if (alertDialog.getWindow() != null) {
            alertDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        alertDialog.setCancelable(true);
        alertDialog.show();
    }
}
