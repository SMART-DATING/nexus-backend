package ru.nexus.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.nexus.dto.Requests.*;
import ru.nexus.entity.*;
import ru.nexus.repository.*;

@Service
@Transactional
public class NexusService {

  private final InterestRepository catalogue;
  private final UserInterestRepository selections;
  private final PreferenceRepository preferenceStore;
  private final UserAccountRepository users;
  private final ProfilePropertyRepository properties;
  private final ReactionRepository reactions;
  private final PairMatchRepository matches;
  private final ChatMessageRepository messages;
  private final NoticeRepository notices;
  private final SessionTokenRepository sessions;
  private final BCryptPasswordEncoder encoder;
  private final GalleryService gallery;
  private final ContextService contexts;
  private final BlockService blocks;
  private final ChatPreferenceRepository chatPreferences;
  private final PresenceService presence;

  public NexusService(
    UserAccountRepository u,
    ProfilePropertyRepository p,
    ReactionRepository r,
    PairMatchRepository m,
    ChatMessageRepository c,
    NoticeRepository n,
    SessionTokenRepository s,
    BCryptPasswordEncoder e,
    InterestRepository catalogue,
    UserInterestRepository selections,
    PreferenceRepository preferenceStore,
    GalleryService gallery,
    ContextService contexts,
    BlockService blocks,
    ChatPreferenceRepository chatPreferences,
    PresenceService presence
  ) {
    this.catalogue = catalogue;
    this.selections = selections;
    this.preferenceStore = preferenceStore;
    this.gallery = gallery;
    this.contexts = contexts;
    this.blocks = blocks;
    this.chatPreferences = chatPreferences;
    this.presence = presence;
    users = u;
    properties = p;
    reactions = r;
    matches = m;
    messages = c;
    notices = n;
    sessions = s;
    encoder = e;
  }

  public static ResponseStatusException fail(int status, String message) {
    return new ResponseStatusException(HttpStatus.valueOf(status), message);
  }

  public Map<String, Object> authenticate(Credentials c, boolean register) {
    if (
      c.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
    ) throw fail(400, "Пароль должен занимать не более 72 байт UTF-8");
    String email = c.email().trim().toLowerCase(Locale.ROOT);
    UserAccount u;
    if (register) {
      if (users.findByEmail(email).isPresent()) throw fail(
        409,
        "Этот email уже зарегистрирован"
      );
      u = new UserAccount();
      u.email = email;
      u.passwordHash = encoder.encode(c.password());
      users.saveAndFlush(u);
      Preference preference = new Preference();
      preference.user = u;
      preferenceStore.save(preference);
    } else {
      u = users
        .findByEmail(email)
        .orElseThrow(() -> fail(401, "Неверный email или пароль"));
      if (!encoder.matches(c.password(), u.passwordHash)) throw fail(
        401,
        "Неверный email или пароль"
      );
    }
    u.lastActiveAt = Instant.now();
    byte[] bytes = new byte[32];
    new java.security.SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder()
      .withoutPadding()
      .encodeToString(bytes);
    SessionToken s = new SessionToken();
    s.userId = u.id;
    s.tokenHash = hash(token);
    s.expiresAt = Instant.now().plusSeconds(86400);
    sessions.save(s);
    return Map.of(
      "accessToken",
      token,
      "tokenType",
      "Bearer",
      "expiresAt",
      s.expiresAt,
      "user",
      me(u.id)
    );
  }

  private String hash(String s) {
    try {
      return HexFormat.of().formatHex(
        java.security.MessageDigest.getInstance("SHA-256").digest(
          s.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Long identify(String header) {
    if (header == null || !header.startsWith("Bearer ")) throw fail(
      401,
      "Войдите в аккаунт"
    );
    Long id = sessions
      .findByTokenHash(hash(header.substring(7)))
      .filter(s -> s.expiresAt.isAfter(Instant.now()))
      .map(s -> s.userId)
      .orElseThrow(() -> fail(401, "Сессия истекла. Войдите снова"));
    presence.touch(id);
    return id;
  }

  public void logout(String header) {
    gallery.revoke(identify(header));
    sessions.deleteByTokenHash(hash(header.substring(7)));
  }

  private UserAccount user(Long id) {
    return users
      .findById(id)
      .orElseThrow(() -> fail(404, "Пользователь не найден"));
  }

  public List<String> interestCatalogue() {
    return catalogue
      .findAllByOrderByIdAsc()
      .stream()
      .map(i -> i.name)
      .toList();
  }

  private Set<String> interestNames(Long id) {
    return selections
      .findByUserId(id)
      .stream()
      .map(x -> x.interest.name)
      .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
  }

  private Preference preferenceFor(UserAccount u) {
    return preferenceStore.findByUserId(u.id).orElseGet(() -> {
      Preference p = new Preference();
      p.user = u;
      return preferenceStore.save(p);
    });
  }

  public Map<String, Object> me(Long id) {
    UserAccount u = user(id);
    Preference pref = preferenceFor(u);
    return Map.of(
      "id",
      id,
      "email",
      u.email,
      "discoveryHidden",
      Boolean.TRUE.equals(u.discoveryHidden),
      "profile",
      profile(id, true),
      "preferences",
      Map.of("minAge", pref.minAge, "maxAge", pref.maxAge)
    );
  }

  public Map<String, Object> profile(Long id, boolean own) {
    return profileFor(id, own ? id : null);
  }

  public Map<String, Object> profileFor(Long id, Long viewer) {
    boolean own = id.equals(viewer);
    UserAccount u = user(id);
    if (!gallery.canView(id, viewer)) throw fail(404, "Анкета недоступна");
    var all = properties.findByUserId(id);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("userId", id);
    out.putAll(gallery.view(id, viewer));
    var visible = (List<Map<String, Object>>) out.get("photos");
    if (!visible.isEmpty()) out.put("avatarUrl", visible.get(0).get("url"));
    if (own) {
      out.put("contextCount", contexts.count(id));
      out.put("contextCharacterCount", contexts.characterCount(id));
    }
    out.put("interests", interestNames(id));
    out.put(
      "properties",
      all
        .stream()
        .filter(p -> own || p.visible)
        .map(p ->
          Map.of("name", p.name, "value", p.value, "visible", p.visible)
        )
        .toList()
    );
    return out;
  }

  public Map<String, Object> saveProfile(Long id, Profile input) {
    var map = new HashMap<String, Property>();
    input.properties().forEach(p -> map.put(p.name(), p));
    if (
      map.size() != 4 ||
      !map
        .keySet()
        .containsAll(Set.of("display_name", "bio", "birth_date", "city"))
    ) throw fail(400, "Нужны имя, описание, дата рождения и город");
    if (
      map.get("display_name").value().isBlank() ||
      map.get("display_name").value().length() > 60 ||
      !map.get("display_name").visible()
    ) throw fail(400, "Укажите публичное имя до 60 символов");
    if (
      map.get("city").value().isBlank() || map.get("city").value().length() > 80
    ) throw fail(400, "Укажите город до 80 символов");
    try {
      int age = Period.between(
        LocalDate.parse(map.get("birth_date").value()),
        LocalDate.now()
      ).getYears();
      if (age < 18 || age > 100) throw new Exception();
    } catch (Exception e) {
      throw fail(400, "Возраст должен быть от 18 до 100 лет");
    }
    if (!interestCatalogue().containsAll(input.interests())) throw fail(
      400,
      "Неизвестный интерес"
    );
    var old = properties.findByUserId(id);
    for (Property p : input.properties()) {
      ProfileProperty row = old
        .stream()
        .filter(x -> x.name.equals(p.name()))
        .findFirst()
        .orElseGet(ProfileProperty::new);
      row.userId = id;
      row.name = p.name();
      row.value = p.value().trim();
      row.visible = p.visible();
      properties.save(row);
    }
    UserAccount u = user(id);
    var existing = selections.findByUserId(id);
    selections.deleteAll(
      existing
        .stream()
        .filter(x -> !input.interests().contains(x.interest.name))
        .toList()
    );
    Set<String> retained = new HashSet<>();
    existing.forEach(x -> retained.add(x.interest.name));
    for (Interest interest : catalogue.findByNameIn(input.interests())) {
      if (!retained.contains(interest.name)) {
        UserInterest link = new UserInterest();
        link.user = u;
        link.interest = interest;
        selections.save(link);
      }
    }
    return profile(id, true);
  }

  public Map<String, Integer> preferences(Long id, Preferences p) {
    if (p.minAge() > p.maxAge()) throw fail(
      400,
      "Минимальный возраст больше максимального"
    );
    UserAccount u = user(id);
    Preference pref = preferenceFor(u);
    pref.minAge = p.minAge();
    pref.maxAge = p.maxAge();
    preferenceStore.save(pref);
    return Map.of("minAge", pref.minAge, "maxAge", pref.maxAge);
  }

  public boolean profileComplete(Long id) {
    return properties.findByUserId(id).size() == 4;
  }

  public List<Map<String, Object>> recommend(Long id, int limit) {
    return recommend(id, limit, false);
  }

  private List<Map<String, Object>> recommend(
    Long id,
    int limit,
    boolean includeSkipped
  ) {
    UserAccount me = user(id);
    Preference pref = preferenceFor(me);
    Set<String> ownInterests = interestNames(id);
    double[] ownVector = contexts.vector(id);
    if (!profileComplete(id)) throw fail(
      409,
      "Сначала заполните профиль и интересы"
    );
    var excluded = new HashSet<Long>();
    if (ownVector == null) throw fail(
      409,
      "Добавьте личный рассказ о себе для смыслового подбора"
    );
    excluded.add(id);
    reactions
      .findByActorId(id)
      .stream()
      .filter(r -> r.liked || !includeSkipped)
      .forEach(r -> excluded.add(r.targetId));
    record Candidate(Long id, double score, Set<String> common) {}
    List<Candidate> ranked = new ArrayList<>();
    for (UserAccount u : users.findAll()) {
      if (
        excluded.contains(u.id) ||
        blocks.blocked(id, u.id) ||
        Boolean.TRUE.equals(u.discoveryHidden) ||
        !profileComplete(u.id) ||
        !contexts.hasContext(u.id)
      ) continue;
      String birth = properties
        .findByUserId(u.id)
        .stream()
        .filter(p -> p.name.equals("birth_date"))
        .findFirst()
        .orElseThrow()
        .value;
      int age = Period.between(
        LocalDate.parse(birth),
        LocalDate.now()
      ).getYears();
      if (age < pref.minAge || age > pref.maxAge) continue;
      Set<String> theirs = interestNames(u.id);
      Set<String> common = new TreeSet<>(ownInterests);
      common.retainAll(theirs);
      ranked.add(new Candidate(u.id, SemanticEncoder.cosine(ownVector, contexts.vector(u.id)), common));
    }
    ranked.sort(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::id));
    if (ranked.isEmpty()) return List.of();
    int floor = similarityFloor(ranked.getFirst().score());
    // Issue gallery tickets only for the current band and requested page.
    return ranked.stream()
      .filter(candidate -> similarityFloor(candidate.score()) == floor)
      .limit(Math.max(1, Math.min(limit, 50)))
      .map(candidate -> {
        var p = profileFor(candidate.id(), id);
        p.put("commonInterests", candidate.common());
        p.put("compatibilityScore", candidate.score());
        p.put("similarityFloor", floor);
        p.put("remainingCount", ranked.size() - ranked.indexOf(candidate));
        p.put("matchingBasis", "semantic");
        return p;
      }).toList();
  }

  private static int similarityFloor(double score) {
    return Math.min(9, (int) Math.floor(Math.max(0, score) * 10)) * 10;
  }

  public Map<String, Object> react(Long actor, Long target, boolean like) {
    if (actor.equals(target)) throw fail(400, "Нельзя выбрать себя");
    // Canonical lock order serializes simultaneous reciprocal reactions across server instances.
    users
      .lockById(Math.min(actor, target))
      .orElseThrow(() -> fail(404, "Пользователь не найден"));
    users
      .lockById(Math.max(actor, target))
      .orElseThrow(() -> fail(404, "Пользователь не найден"));
    if (Boolean.TRUE.equals(user(actor).discoveryHidden)) throw fail(
      409,
      "Сначала верните свою анкету в знакомства"
    );
    if (blocks.blocked(actor, target)) throw fail(404, "Анкета недоступна");
    if (Boolean.TRUE.equals(user(target).discoveryHidden)) throw fail(
      404,
      "Анкета недоступна"
    );
    if (!profileComplete(actor) || !profileComplete(target)) throw fail(
      409,
      "Профиль не заполнен"
    );
    if (
      reactions.findByActorIdAndTargetId(actor, target).isPresent()
    ) throw fail(409, "Реакция уже сохранена");
    Reaction r = new Reaction();
    r.actorId = actor;
    r.targetId = target;
    r.liked = like;
    reactions.saveAndFlush(r);
    Long matchId = null;
    if (
      like &&
      reactions
        .findByActorIdAndTargetId(target, actor)
        .filter(x -> x.liked)
        .isPresent()
    ) {
      PairMatch m = new PairMatch();
      m.firstId = Math.min(actor, target);
      m.secondId = Math.max(actor, target);
      matches.saveAndFlush(m);
      matchId = m.id;
      notify(actor, m.id, "У вас новое совпадение!");
      notify(target, m.id, "У вас новое совпадение!");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("liked", like);
    out.put("skipped", !like);
    out.put("matched", matchId != null);
    out.put("matchId", matchId);
    return out;
  }

  public long skippedCount(Long actor) {
    user(actor);
    return reactions.countByActorIdAndLikedFalse(actor);
  }

  public Map<String, Object> discovery(Long id, boolean hidden) {
    var u = users
      .lockById(id)
      .orElseThrow(() -> fail(404, "Пользователь не найден"));
    u.discoveryHidden = hidden;
    users.saveAndFlush(u);
    return Map.of("hidden", hidden);
  }

  public Map<String, Object> nextRecommendations(Long actor, int limit) {
    users
      .lockById(actor)
      .orElseThrow(() -> fail(404, "Пользователь не найден"));
    var items = recommend(actor, limit);
    boolean restarted = false;
    if (items.isEmpty() && !recommend(actor, 1, true).isEmpty()) {
      reactions.deleteSkippedByActorId(actor);
      items = recommend(actor, limit);
      restarted = true;
    }
    return Map.of(
      "items",
      items,
      "skippedCount",
      skippedCount(actor),
      "cycleRestarted",
      restarted
    );
  }

  public Map<String, Object> uploadAvatar(
    Long id,
    org.springframework.web.multipart.MultipartFile file
  ) {
    gallery.replaceFirst(id, file);
    return profile(id, true);
  }

  public Map<String, Object> removeAvatar(Long id) {
    if (gallery.count(id) > 0) gallery.delete(id, gallery.firstId(id));
    return profile(id, true);
  }

  public byte[] avatar(Long id) {
    byte[] image = user(id).avatarImage;
    if (image == null) throw fail(404, "Фото пока нет");
    return image;
  }

  public Map<String, Integer> restartRecommendations(Long actor) {
    // Reactions acquire the same user lock, so a concurrent choice cannot be lost.
    users
      .lockById(actor)
      .orElseThrow(() -> fail(404, "Пользователь не найден"));
    return Map.of("restored", reactions.deleteSkippedByActorId(actor));
  }

  private void notify(Long id, Long matchId, String text) {
    Notice n = new Notice();
    n.userId = id;
    n.matchId = matchId;
    n.text = text;
    notices.save(n);
  }

  private PairMatch accessible(Long id, Long mid) {
    PairMatch m = matches
      .findById(mid)
      .orElseThrow(() -> fail(404, "Совпадение не найдено"));
    if (!id.equals(m.firstId) && !id.equals(m.secondId)) throw fail(
      403,
      "Нет доступа к этому чату"
    );
    if (blocks.blocked(m.firstId, m.secondId)) throw fail(404, "Чат недоступен");
    return m;
  }

  public Map<String, Object> match(Long id, Long mid) {
    PairMatch m = accessible(id, mid);
    ChatPreference prefs = chatPreference(id, mid);
    Long partnerId = id.equals(m.firstId) ? m.secondId : m.firstId;
    var result = new LinkedHashMap<String, Object>(Map.of(
      "id",
      m.id,
      "user",
      profileFor(partnerId, id),
      "createdAt",
      m.createdAt,
      "unreadCount",
      unreadCount(mid, id, prefs),
      "pinned", prefs.pinned,
      "markedUnread", prefs.markedUnread,
      "clearedThroughId", prefs.clearedThroughId
    ));
    result.put("lastActiveAt", user(partnerId).lastActiveAt);
    return result;
  }

  private ChatPreference chatPreference(Long id, Long mid) {
    return chatPreferences.findByUserIdAndMatchId(id, mid).orElseGet(() -> {
      var p = new ChatPreference(); p.userId = id; p.matchId = mid; return p;
    });
  }

  private long unreadCount(Long mid, Long id, ChatPreference prefs) {
    return Math.max(prefs.markedUnread ? 1 : 0, messages.unreadCount(mid, id, prefs.clearedThroughId));
  }

  public Map<String, Object> chatAction(Long id, Long mid, String action) {
    var pair = accessible(id, mid);
    blocks.lockPair(pair.firstId, pair.secondId);
    accessible(id, mid);
    var p = chatPreference(id, mid);
    switch (action) {
      case "pin" -> { p.pinned = true; p.hidden = false; }
      case "unpin" -> p.pinned = false;
      case "unread" -> p.markedUnread = true;
      case "read" -> { p.markedUnread = false; messages.readThrough(mid, id, messages.latestId(mid)); }
      case "clear" -> { p.clearedThroughId = messages.latestId(mid); p.markedUnread = false; }
      case "delete" -> { p.hidden = true; p.pinned = false; p.markedUnread = false; }
      default -> throw fail(400, "Неизвестное действие");
    }
    chatPreferences.saveAndFlush(p);
    return match(id, mid);
  }

  public List<Map<String, Object>> matchList(Long id) {
    return matches
      .findByFirstIdOrSecondIdOrderByIdDesc(id, id)
      .stream()
      .filter(m -> !blocks.blocked(m.firstId, m.secondId))
      .filter(m -> !chatPreference(id, m.id).hidden)
      .map(m -> match(id, m.id))
      .sorted(Comparator.<Map<String, Object>, Boolean>comparing(m -> (Boolean) m.get("pinned")).reversed()
        .thenComparing(m -> (Long) m.get("id"), Comparator.reverseOrder()))
      .toList();
  }

  public List<ChatMessage> history(Long id, Long mid, Long after) {
    accessible(id, mid);
    return messages.findByMatchIdAndIdGreaterThanOrderByIdAsc(
      mid,
      Math.max(after, chatPreference(id, mid).clearedThroughId),
      org.springframework.data.domain.PageRequest.of(0, 100)
    );
  }

  public Map<String, Long> readMessages(Long id, Long mid, Long throughId) {
    var pair = accessible(id, mid);
    blocks.lockPair(pair.firstId, pair.secondId);
    accessible(id, mid);
    messages.readThrough(mid, id, throughId);
    var p = chatPreference(id, mid);
    if (p.markedUnread) { p.markedUnread = false; chatPreferences.save(p); }
    return Map.of("unreadCount", unreadCount(mid, id, p));
  }

  public ChatMessage send(Long id, Long mid, String text) {
    PairMatch m = accessible(id, mid);
    blocks.lockPair(m.firstId, m.secondId);
    accessible(id, mid);
    ChatMessage c = new ChatMessage();
    c.matchId = mid;
    c.senderId = id;
    c.text = text.trim();
    messages.save(c);
    for (Long participant : List.of(m.firstId, m.secondId)) {
      var pref = chatPreference(participant, mid);
      if (pref.hidden) { pref.hidden = false; chatPreferences.save(pref); }
    }
    notify(
      id.equals(m.firstId) ? m.secondId : m.firstId,
      mid,
      "Новое сообщение в чате"
    );
    return c;
  }

  public List<Notice> notifications(Long id) {
    Set<Long> hiddenMatches = new HashSet<>();
    matches.findByFirstIdOrSecondIdOrderByIdDesc(id, id).stream()
      .filter(m -> blocks.blocked(m.firstId, m.secondId) || chatPreference(id, m.id).hidden).forEach(m -> hiddenMatches.add(m.id));
    return notices.findTop100ByUserIdOrderByIdDesc(id).stream()
      .filter(n -> !hiddenMatches.contains(n.matchId)).toList();
  }

  public Notice read(Long id, Long nid) {
    Notice n = notices
      .findById(nid)
      .orElseThrow(() -> fail(404, "Уведомление не найдено"));
    if (!id.equals(n.userId)) throw fail(403, "Нет доступа");
    if (n.matchId != null) accessible(id, n.matchId);
    n.seen = true;
    return n;
  }
}
