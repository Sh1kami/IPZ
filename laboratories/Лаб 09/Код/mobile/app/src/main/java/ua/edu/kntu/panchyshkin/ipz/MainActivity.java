package ua.edu.kntu.panchyshkin.ipz;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private EditText query;
    private LinearLayout results;
    private TextView counter;
    private final String[] jobs={"Java Developer", "Python Developer", "Junior Java Developer"};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,24);
        scroll.addView(root);setContentView(scroll);
        TextView heading=new TextView(this);heading.setText("Сервіс пошуку роботи");heading.setTextSize(24);root.addView(heading);
        TextView author=new TextView(this);author.setText("Панчишкін Богдан Олександрович · 3ПРс/3");root.addView(author);
        query=new EditText(this);query.setSingleLine(true);query.setHint("Посада");query.setContentDescription("search-query");root.addView(query);
        Button search=new Button(this);search.setText("Знайти");search.setContentDescription("search-submit");root.addView(search);
        Button reset=new Button(this);reset.setText("Скинути");reset.setContentDescription("search-reset");root.addView(reset);
        counter=new TextView(this);counter.setContentDescription("job-count");root.addView(counter);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results);
        search.setOnClickListener(v->filter());reset.setOnClickListener(v->{query.setText("");filter();});
        if(state!=null)query.setText(state.getString("query",""));
        filter();
    }
    private void filter(){
        results.removeAllViews();int count=0;
        String value=query.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(String job:jobs)if(job.toLowerCase(Locale.ROOT).contains(value)){
            count++;Button item=new Button(this);item.setText(job);item.setContentDescription("job-"+count);
            item.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle(job).setMessage("Навчальна вакансія. Java або Python, Київ чи Львів.").setPositiveButton("Закрити",(d,w)->d.dismiss()).show());
            results.addView(item);
        }
        counter.setText("Знайдено вакансій: "+count);
    }
    @Override public void onSaveInstanceState(Bundle state){state.putString("query",query.getText().toString());super.onSaveInstanceState(state);}
}
