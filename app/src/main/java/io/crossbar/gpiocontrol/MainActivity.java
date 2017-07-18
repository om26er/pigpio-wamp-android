package io.crossbar.gpiocontrol;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.crossbario.gpiocontrol.R;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.crossbar.autobahn.wamp.Client;
import io.crossbar.autobahn.wamp.Session;
import io.crossbar.autobahn.wamp.auth.AnonymousAuth;
import io.crossbar.autobahn.wamp.interfaces.IAuthenticator;
import io.crossbar.autobahn.wamp.interfaces.ITransport;
import io.crossbar.autobahn.wamp.transports.NettyTransport;
import io.crossbar.autobahn.wamp.types.CallResult;
import io.crossbar.autobahn.wamp.types.ExitInfo;
import io.crossbar.autobahn.wamp.types.SessionDetails;

public class MainActivity extends AppCompatActivity {

    private ListView mListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Session session = new Session();
        session.addOnJoinListener(this::onJoinHandler1);
        List<ITransport> transports = new ArrayList<>();
        transports.add(new NettyTransport("ws://46.101.72.82:8080/ws"));
        List<IAuthenticator> authenticators = new ArrayList<>();
        authenticators.add(new AnonymousAuth());
        Client client = new Client(transports);
        client.add(session, "realm1", authenticators);

        CompletableFuture<ExitInfo> exitFuture = client.connect();
        exitFuture.thenRun(() -> System.out.println("That was an exit."));

        List<Object> objects = new ArrayList<>();
        objects.add(20);

        mListView = findViewById(R.id.gpio_pin_controls);
        mListView.setOnItemClickListener((adapterView, view, i, l) -> {

        });
    }

    public void onJoinHandler1(Session session, SessionDetails details) {
        session.subscribe("io.crossbar.pigpio-wamp.turned_on", this::turnedOnListener, null);
        session.subscribe("io.crossbar.pigpio-wamp.turned_off", this::turnedOffListener, null);

        // Get state of all GPIO pins.
        CompletableFuture<List<GPIOState>> results = session.call(
                "io.crossbar.pigpio-wamp.get_states",
                new TypeReference<List<GPIOState>>() {}, null);
        results.thenAcceptAsync(gpioStates -> {
            MyAdapter adapter = new MyAdapter(
                    getApplicationContext(), R.layout.gpio_control_row, gpioStates, session);
            runOnUiThread(() -> mListView.setAdapter(adapter));
        });
    }

    private void turnedOnListener(int gpioPin) {
    }

    private void turnedOffListener(int gpioPin) {
    }

    static class GPIOState {
        public int pin_number;
        public int value;

        public String direction;
        public String value_verbose;

        public GPIOState() {

        }
    }

    private class MyAdapter extends ArrayAdapter {

        private Session mSession;

        public MyAdapter(@NonNull Context context, int resource, @NonNull List<GPIOState> objects,
                         Session session) {
            super(context, resource, objects);
            mSession = session;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.gpio_control_row, parent, false);
                holder = new ViewHolder();
                holder.textView = convertView.findViewById(R.id.text_pin_number);
                holder.toggleSwitch = convertView.findViewById(R.id.switch_toggle);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            GPIOState item = (GPIOState) getItem(position);
            holder.toggleSwitch.setChecked(Objects.equals(item.value_verbose, "on"));
            holder.toggleSwitch.setOnCheckedChangeListener((compoundButton, checked) -> {
                if (checked) {
                    CompletableFuture<CallResult> result = mSession.call(
                            "io.crossbar.pigpio-wamp.turn_on",
                            new TypeReference<CallResult>() {}, null, item.pin_number);
                } else {
                    CompletableFuture<CallResult> result = mSession.call(
                            "io.crossbar.pigpio-wamp.turn_off",
                            new TypeReference<CallResult>() {}, null, item.pin_number);
                }
            });
            holder.textView.setText(Objects.equals(item.value_verbose, "off")
                    ? "Switch on pin# " + item.pin_number : "Switch off pin# " + item.pin_number);
            return convertView;
        }
    }

    static class ViewHolder {
        TextView textView;
        Switch toggleSwitch;
    }
}
