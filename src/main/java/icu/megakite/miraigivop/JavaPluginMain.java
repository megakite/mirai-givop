package icu.megakite.miraigivop;

import kotlin.Lazy;
import kotlin.LazyKt;
import net.mamoe.mirai.console.permission.*;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.contact.Group;
import net.mamoe.mirai.event.Event;
import net.mamoe.mirai.event.EventChannel;
import net.mamoe.mirai.event.GlobalEventChannel;
import net.mamoe.mirai.event.events.GroupMessageEvent;
import net.mamoe.mirai.message.data.Audio;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.utils.ExternalResource;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static icu.megakite.miraigivop.WrappersKt.convertAsync;

public final class JavaPluginMain extends JavaPlugin {
    public static final JavaPluginMain INSTANCE = new JavaPluginMain();

    private JavaPluginMain() {
        super(new JvmPluginDescriptionBuilder("icu.megakite.mirai-givop", "0.1.0")
                .name("Genshin Voice-over Player")
                .info("Randomly plays specified Genshin characters' voice-overs")
                .build());
    }

    private static final HashMap<String, String> CHARACTERS = parseList();

    private static HashMap<String, String> parseList() {
        final File listOfCharacters = new File("list_of_characters.txt");
        final Scanner sc;
        try {
            sc = new Scanner(listOfCharacters);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        HashMap<String, String> map = new HashMap<>();
        while (sc.hasNextLine()) {
            final String currentLine = sc.nextLine();
            final String[] words = currentLine.split(" ");
            for (int i = 1; i < words.length; i++) {
                map.put(words[i], words[0]);
            }
        }

        return map;
    }

    @Override
    public void onEnable() {
        getLogger().info("loaded " + CHARACTERS.size() + " aliases.");

        reloadPluginConfig(MiraiGivopConfig.INSTANCE);
        getLogger().info("loaded configuration.");

        EventChannel<Event> eventChannel = GlobalEventChannel.INSTANCE.parentScope(this);
        eventChannel.subscribeAlways(GroupMessageEvent.class, g -> {
            final MessageChain chain = g.getMessage();
            final Group group = g.getGroup();

            final String str = chain.contentToString();
            if (str.endsWith("配语音") && str.length() >= "#*配语音".length()) {
                try {
                    final String root = matchUrl(str);
                    final String body = getBody(root);
                    final String target = randomTarget(body);
                    final String href = randomElement(target);
                    final String url = extractUrl(href);
                    getLogger().info("processing voice: " + url);

                    try (final ExternalResource silk = urlToSilk(url)) {
                        Audio audio = group.uploadAudio(silk);
                        group.sendMessage(audio);
                    }
                } catch (Exception e) {
                    getLogger().error(e.getMessage());
                }
            }
        });

        miraiGivopPermission.getValue();
    }

    private static String matchUrl(String str) throws IllegalStateException {
        String url = "https://genshin-impact.fandom.com/wiki/";

        final String name = str.substring(0, str.length() - "*配语音".length());
        final String value = CHARACTERS.get(name);
        if (value == null) {
            throw new IllegalStateException("specified character doesn't exist");
        }
        url += value;

        switch (str.charAt(str.length() - 1 - "配语音".length())) {
            case '中':
                url += "/Voice-Overs/Chinese";
                break;
            case '日':
                url += "/Voice-Overs/Japanese";
                break;
            case '英':
                url += "/Voice-Overs";
                break;
            case '韩':
                url += "/Voice-Overs/Korean";
                break;
            default:
                throw new IllegalStateException("invalid language");
        }

        return url;
    }

    private static String getBody(String url) throws IOException {
        final OkHttpClient client = new OkHttpClient();
        final Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            return Objects.requireNonNull(response.body()).string();
        }
    }

    private static String randomTarget(String res) throws IllegalStateException {
        final Pattern re;
        final double rnd = Math.random();
        final double storyVoiceProbability = MiraiGivopConfig.INSTANCE.getStoryVoiceProbability();
        if (rnd < storyVoiceProbability) {
            re = Pattern.compile("id=\"Story\"(.*?)</table>", Pattern.DOTALL);
        } else {
            re = Pattern.compile("id=\"Combat\"(.*?)</table>", Pattern.DOTALL);
        }
        final Matcher matcher = re.matcher(res);
        if (!matcher.find()) {
            throw new IllegalStateException("cannot find list of voices");
        }

        return matcher.group();
    }

    private static String randomElement(String target) throws IllegalStateException {
        final Pattern re = Pattern.compile("<span class=\"audio-button custom-theme hidden\"(.*?)</span>");
        final Matcher matcher = re.matcher(target);
        ArrayList<Integer[]> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new Integer[]{matcher.start(), matcher.end()});
        }

        if (ranges.isEmpty()) {
            throw new IllegalStateException("cannot find audio element in body");
        }
        final int rndIdx = (int) (Math.random() * ranges.size());

        return target.substring(ranges.get(rndIdx)[0], ranges.get(rndIdx)[1]);
    }

    private static String extractUrl(String elem) throws IllegalStateException {
        final Pattern re = Pattern.compile("\"https://(.*?)\"");
        final Matcher matcher = re.matcher(elem);
        if (!matcher.find()) {
            throw new IllegalStateException("cannot find source url in element");
        }

        return elem.substring(matcher.start() + "\"".length(), matcher.end() - "\"".length());
    }

    private static ExternalResource urlToSilk(String url) throws IOException {
        final OkHttpClient client = new OkHttpClient();
        final Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            final ResponseBody body = response.body();
            final InputStream stream = Objects.requireNonNull(body).byteStream();

            try (final ExternalResource resource = ExternalResource.create(stream)) {
                return convertAsync(resource).join();
            }
        }
    }

    public static final Lazy<Permission> miraiGivopPermission = LazyKt.lazy(() -> {
        try {
            return PermissionService.getInstance().register(
                    INSTANCE.permissionId("mirai-givop-permission"),
                    "permission of Genshin Voice-over Player",
                    INSTANCE.getParentPermission()
            );
        } catch (PermissionRegistryConflictException e) {
            throw new RuntimeException(e);
        }
    });
}
