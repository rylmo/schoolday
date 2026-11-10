// Schoolday iPhone setup: a small wizard that prepares a Windows PC and walks through
// installing SideStore (via iloader) and Schoolday on an iPhone.
//
// It never sees the user's Apple Account: that is entered only into iloader itself.
// Built with the C# compiler that ships with Windows; see build.ps1.
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.Net;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("Schoolday iPhone Setup")]
[assembly: AssemblyProduct("Schoolday")]
[assembly: AssemblyVersion("1.0.0.0")]

static class L
{
    public static readonly bool Czech = CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "cs"
        || Array.IndexOf(Environment.GetCommandLineArgs(), "--cs") >= 0;
    public static string T(string en, string cs) { return Czech ? cs : en; }
}

static class Ui
{
    public static readonly Color Bg = Color.FromArgb(0x0F, 0x10, 0x12);
    public static readonly Color Raised = Color.FromArgb(0x1E, 0x1F, 0x23);
    public static readonly Color Line = Color.FromArgb(0x26, 0x27, 0x2B);
    public static readonly Color Text = Color.FromArgb(0xEC, 0xE9, 0xE2);
    public static readonly Color Muted = Color.FromArgb(0x9A, 0x96, 0x8E);
    public static readonly Color Accent = Color.FromArgb(0xF2, 0xA6, 0x5A);
    public static readonly Color Good = Color.FromArgb(0x7F, 0xD1, 0xA8);
    public static readonly Color Bad = Color.FromArgb(0xFF, 0x9C, 0x8A);

    public static Font F(float size, FontStyle style = FontStyle.Regular)
    {
        return new Font("Segoe UI", size, style, GraphicsUnit.Point);
    }

    public static Button Button(string text, bool primary)
    {
        var b = new Button();
        b.Text = text;
        b.FlatStyle = FlatStyle.Flat;
        b.FlatAppearance.BorderSize = 0;
        b.BackColor = primary ? Accent : Raised;
        b.ForeColor = primary ? Bg : Text;
        b.FlatAppearance.MouseOverBackColor = primary ? Color.FromArgb(0xFF, 0xB8, 0x74) : Line;
        b.FlatAppearance.MouseDownBackColor = primary ? Color.FromArgb(0xD9, 0x90, 0x48) : Line;
        b.Font = F(10.5f, FontStyle.Bold);
        b.Cursor = Cursors.Hand;
        b.AutoSize = false;
        b.Height = 42;
        b.UseVisualStyleBackColor = false;
        return b;
    }
}

class Step
{
    public string Eyebrow, Title, Body;
    public string Qr, QrCaption;
    public Action<SetupForm, FlowLayoutPanel> Extra;
    /// Shows the list of programs that can be removed under a shorter body.
    public bool Checklist;
}

/// A program this setup may have installed, found in Windows' list of installed programs.
class Removable
{
    public string Name, Command;
    public override string ToString() { return Name; }
}

class SetupForm : Form
{
    const string IpaUrl = "https://github.com/rylmo/schoolday/releases/download/ios-test/Schoolday-iOS.ipa";
    const string ILoaderMsi = "https://github.com/nab138/iloader/releases/latest/download/iloader-windows-x64.msi";
    const string ITunesUrl = "https://www.apple.com/itunes/download/win64";
    const string AppleDevicesStore = "https://apps.microsoft.com/detail/9np83lwlpz9k";
    const string GuideUrl = "https://docs.sidestore.io/docs/installation/install";

    readonly List<Step> steps = new List<Step>();
    readonly Label eyebrow, title, body, status;
    readonly PictureBox qr;
    readonly Label qrCaption;
    readonly FlowLayoutPanel actions;
    readonly Button back, next;
    readonly ProgressBar progress;
    readonly Panel dots;
    readonly CheckedListBox cleanup;
    int index;
    bool busy;

    public SetupForm()
    {
        Text = L.T("Schoolday iPhone Setup", "Instalace Schoolday na iPhone");
        ClientSize = new Size(780, 600);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = Ui.Bg;
        ForeColor = Ui.Text;
        Font = Ui.F(10.5f);
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch (Exception) { }

        eyebrow = Add(new Label { Location = new Point(36, 30), Size = new Size(700, 22), ForeColor = Ui.Accent, Font = Ui.F(9.5f, FontStyle.Bold) });
        title = Add(new Label { Location = new Point(32, 54), Size = new Size(710, 52), ForeColor = Ui.Text, Font = Ui.F(22f, FontStyle.Bold) });
        body = Add(new Label { Location = new Point(36, 118), Size = new Size(470, 330), ForeColor = Ui.Text, Font = Ui.F(11f) });
        qr = Add(new PictureBox { Location = new Point(536, 122), Size = new Size(208, 208), SizeMode = PictureBoxSizeMode.Zoom, Visible = false });
        qrCaption = Add(new Label { Location = new Point(536, 338), Size = new Size(208, 80), ForeColor = Ui.Muted, Font = Ui.F(9.5f), TextAlign = ContentAlignment.TopCenter, Visible = false });
        actions = Add(new FlowLayoutPanel { Location = new Point(36, 452), Size = new Size(710, 46), FlowDirection = FlowDirection.LeftToRight, WrapContents = false, BackColor = Ui.Bg });
        status = Add(new Label { Location = new Point(36, 500), Size = new Size(710, 22), ForeColor = Ui.Muted, Font = Ui.F(9.5f) });
        cleanup = Add(new CheckedListBox
        {
            Location = new Point(36, 262), Size = new Size(710, 176), CheckOnClick = true, Visible = false,
            BackColor = Ui.Raised, ForeColor = Ui.Text, BorderStyle = BorderStyle.None, Font = Ui.F(11f),
        });
        progress = Add(new ProgressBar { Location = new Point(36, 524), Size = new Size(710, 4), Visible = false });
        dots = Add(new Panel { Location = new Point(36, 548), Size = new Size(300, 20) });
        dots.Paint += PaintDots;
        back = Add(Ui.Button(L.T("Back", "Zpět"), false));
        back.SetBounds(476, 538, 130, 42);
        next = Add(Ui.Button(L.T("Next", "Další"), true));
        next.SetBounds(616, 538, 130, 42);
        back.Click += delegate { if (index > 0) Show(index - 1); };
        next.Click += delegate { if (index < steps.Count - 1) Show(index + 1); else Close(); };

        BuildSteps();
        Show(0);
    }

    T Add<T>(T c) where T : Control
    {
        var label = c as Label;
        if (label != null) label.UseMnemonic = false; // keeps the "&" in "VPN & Device Management"
        Controls.Add(c);
        return c;
    }

    void PaintDots(object sender, PaintEventArgs e)
    {
        e.Graphics.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
        int x = 0;
        for (int i = 0; i < steps.Count; i++)
        {
            int w = i == index ? 26 : 9;
            using (var b = new SolidBrush(i <= index ? Ui.Accent : Ui.Line))
                e.Graphics.FillRectangle(b, x, 6, w, 5);
            x += w + 7;
        }
    }

    public void Show(int i)
    {
        index = i;
        var s = steps[i];
        eyebrow.Text = s.Eyebrow.ToUpperInvariant();
        title.Text = s.Title;
        body.Text = s.Body;
        bool hasQr = s.Qr != null;
        body.Width = hasQr ? 470 : 710;
        body.Height = s.Checklist ? 136 : 330;
        cleanup.Visible = s.Checklist;
        if (s.Checklist) FillCleanup();
        qr.Visible = hasQr;
        qrCaption.Visible = hasQr;
        if (hasQr)
        {
            qr.Image = LoadImage(s.Qr);
            qrCaption.Text = s.QrCaption;
        }
        actions.Controls.Clear();
        status.Text = "";
        status.ForeColor = Ui.Muted;
        progress.Visible = false;
        if (s.Extra != null) s.Extra(this, actions);
        back.Visible = i > 0;
        next.Text = i == steps.Count - 1 ? L.T("Finish", "Dokončit") : L.T("Next", "Další");
        dots.Invalidate();
    }

    static Image LoadImage(string name)
    {
        using (var s = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
        using (var m = new MemoryStream())
        {
            s.CopyTo(m);
            return new Bitmap(new MemoryStream(m.ToArray()));
        }
    }

    void BuildSteps()
    {
        const int total = 8;
        Func<int, string> n = delegate (int k) { return L.T("Step " + k + " of " + total, "Krok " + k + " z " + total); };

        steps.Add(new Step
        {
            Eyebrow = n(1),
            Title = L.T("Schoolday on your iPhone", "Schoolday na tvém iPhonu"),
            Body = L.T(
                "Apple doesn't let apps be installed from outside the App Store, so Schoolday uses SideStore, which signs it with your own free Apple Account. This wizard takes about 15 minutes.\n\n" +
                "You'll need:\n" +
                "•  This PC (64-bit Windows)\n" +
                "•  An iPhone with iOS 16 or newer, with a passcode set\n" +
                "•  A USB cable and Wi-Fi\n" +
                "•  An Apple Account (a separate free one is fine)\n\n" +
                "Good to know: a free account allows 3 sideloaded apps at once, SideStore included. Apps last 7 days, so the last step sets your iPhone up to renew them every day by itself.",
                "Apple nedovoluje instalovat aplikace mimo App Store, takže Schoolday používá SideStore, který ji podepíše tvým vlastním bezplatným účtem Apple. Průvodce zabere asi 15 minut.\n\n" +
                "Budeš potřebovat:\n" +
                "•  Tento počítač (64bitové Windows)\n" +
                "•  iPhone se systémem iOS 16 nebo novějším a nastaveným kódem\n" +
                "•  Kabel USB a Wi-Fi\n" +
                "•  Účet Apple (klidně zvláštní bezplatný)\n\n" +
                "Dobré vědět: bezplatný účet umožňuje najednou 3 aplikace včetně SideStoru. Aplikace platí 7 dní, proto poslední krok nastaví iPhone, aby je každý den sám obnovoval."),
        });

        steps.Add(new Step
        {
            Eyebrow = n(2),
            Title = L.T("Install iTunes on this PC", "Nainstaluj do počítače iTunes"),
            Body = L.T(
                "iTunes provides the driver that lets this PC talk to your iPhone. Use the version from Apple's website, as SideStore's guide recommends.\n\n" +
                "If iTunes doesn't work, try the Apple Devices app from the Microsoft Store instead.",
                "iTunes obsahuje ovladač, díky kterému se počítač domluví s iPhonem. Použij verzi z webu Applu, jak doporučuje návod SideStoru.\n\n" +
                "Pokud iTunes nebude fungovat, zkus místo toho aplikaci Zařízení Apple z Microsoft Storu."),
            Extra = delegate (SetupForm f, FlowLayoutPanel p)
            {
                bool found = AppleDriverInstalled();
                f.SetStatus(found ? L.T("The Apple driver is already installed. You can continue.", "Ovladač Apple už je nainstalovaný. Můžeš pokračovat.")
                                  : L.T("The Apple driver wasn't found on this PC.", "Ovladač Apple se v počítači nenašel."), found ? Ui.Good : Ui.Muted);
                AddAction(p, L.T("Download and install iTunes", "Stáhnout a nainstalovat iTunes"), !found, 300, f.InstallITunes);
                AddAction(p, L.T("Open Apple Devices", "Otevřít Zařízení Apple"), false, 220, delegate { Open(AppleDevicesStore); });
            },
        });

        steps.Add(new Step
        {
            Eyebrow = n(3),
            Title = L.T("Get LocalDevVPN on your iPhone", "Stáhni do iPhonu LocalDevVPN"),
            Body = L.T(
                "SideStore needs this free app to install and renew apps. It doesn't send your traffic anywhere.\n\n" +
                "1.  Scan the code with your iPhone's camera.\n" +
                "2.  Install LocalDevVPN from the App Store.\n" +
                "3.  Open it and tap Connect.\n" +
                "4.  Tap Allow and enter your passcode.",
                "SideStore tuhle bezplatnou aplikaci potřebuje k instalaci a obnovování aplikací. Tvůj provoz nikam neposílá.\n\n" +
                "1.  Naskenuj kód fotoaparátem iPhonu.\n" +
                "2.  Nainstaluj LocalDevVPN z App Storu.\n" +
                "3.  Otevři ji a klepni na Connect.\n" +
                "4.  Klepni na Povolit a zadej kód."),
            Qr = "qr_vpn.png",
            QrCaption = L.T("LocalDevVPN on the App Store", "LocalDevVPN v App Storu"),
        });

        steps.Add(new Step
        {
            Eyebrow = n(4),
            Title = L.T("Install SideStore with iloader", "Nainstaluj SideStore přes iloader"),
            Body = L.T(
                "1.  Connect your iPhone to this PC with the USB cable. If it asks, tap Trust and enter your passcode.\n" +
                "2.  Click the button below to get iloader, SideStore's official installer.\n" +
                "3.  In iloader, sign in with your Apple Account. Only iloader sees it; this wizard doesn't.\n" +
                "4.  Select your iPhone, then choose Install SideStore (Stable).\n" +
                "5.  Wait until iloader says it's finished.\n\n" +
                "No device found? Unlock the iPhone, plug it straight into the PC (not a hub) and try another cable, as some only charge. If it still isn't listed, restart the PC.",
                "1.  Připoj iPhone k počítači kabelem USB. Pokud se zeptá, klepni na Důvěřovat a zadej kód.\n" +
                "2.  Tlačítkem níže získáš iloader, oficiální instalátor SideStoru.\n" +
                "3.  V iloaderu se přihlas účtem Apple. Vidí ho jen iloader, tento průvodce ne.\n" +
                "4.  Vyber svůj iPhone a zvol Install SideStore (Stable).\n" +
                "5.  Počkej, až iloader oznámí dokončení.\n\n" +
                "Nenašel se žádný iPhone? Odemkni ho, připoj ho přímo do počítače (ne přes rozbočovač) a zkus jiný kabel, protože některé umí jen nabíjet. Když se pořád nezobrazí, restartuj počítač."),
            Extra = delegate (SetupForm f, FlowLayoutPanel p)
            {
                AddAction(p, L.T("Download and open iloader", "Stáhnout a otevřít iloader"), true, 300, f.InstallILoader);
                AddAction(p, L.T("Open the SideStore guide", "Otevřít návod SideStoru"), false, 240, delegate { Open(GuideUrl); });
            },
        });

        steps.Add(new Step
        {
            Eyebrow = n(5),
            Title = L.T("Finish SideStore on your iPhone", "Dokonči SideStore v iPhonu"),
            Body = L.T(
                "1.  Go to Settings > General > VPN & Device Management, choose your Apple Account and tap Trust.\n" +
                "2.  If the iPhone asks for Developer Mode: go to Settings > Privacy & Security, turn on Developer Mode at the bottom, and let the phone restart.\n" +
                "3.  Open LocalDevVPN and tap Connect.\n" +
                "4.  Open SideStore and sign in with the same Apple Account.\n" +
                "5.  In My Apps, tap the 7 DAYS button next to SideStore. If asked about a certificate, tap Yes or Refresh Now.\n\n" +
                "When SideStore sends you back to the Home Screen with a notification, it's ready.\n\n" +
                "Sign-in error -80045? In SideStore's Settings, turn off On-Device Anisette, choose the SideStore (New) server, turn off auto rotation and tap Reset adi.pb. Then reopen SideStore and sign in once.",
                "1.  Otevři Nastavení > Obecné > Správa VPN a zařízení, vyber svůj účet Apple a klepni na Důvěřovat.\n" +
                "2.  Pokud iPhone chce Režim pro vývojáře: v Nastavení > Soukromí a zabezpečení ho úplně dole zapni a nech telefon restartovat.\n" +
                "3.  Otevři LocalDevVPN a klepni na Connect.\n" +
                "4.  Otevři SideStore a přihlas se stejným účtem Apple.\n" +
                "5.  V části My Apps klepni u SideStoru na 7 DAYS. Pokud se zeptá na certifikát, klepni na Yes nebo Refresh Now.\n\n" +
                "Až tě SideStore vrátí na plochu a ukáže oznámení, je připravený.\n\n" +
                "Chyba přihlášení -80045? V Nastavení SideStoru vypni On-Device Anisette, vyber server SideStore (New), vypni auto rotation a klepni na Reset adi.pb. Pak SideStore znovu otevři a jednou se přihlas."),
        });

        steps.Add(new Step
        {
            Eyebrow = n(6),
            Title = L.T("Install Schoolday", "Nainstaluj Schoolday"),
            Body = L.T(
                "1.  Scan the code and open it in Safari.\n" +
                "2.  Tap Download.\n" +
                "3.  Check that LocalDevVPN is connected.\n" +
                "4.  In SideStore, go to My Apps and tap + at the top.\n" +
                "5.  Choose Schoolday-iOS.ipa from Downloads.\n\n" +
                "There are no automatic updates on iPhone yet: to update, repeat this step.",
                "1.  Naskenuj kód a otevři ho v Safari.\n" +
                "2.  Klepni na Stáhnout.\n" +
                "3.  Zkontroluj, že je LocalDevVPN připojená.\n" +
                "4.  V SideStoru otevři My Apps a nahoře klepni na +.\n" +
                "5.  Ve Stažených vyber Schoolday-iOS.ipa.\n\n" +
                "Automatické aktualizace na iPhonu zatím nejsou: při aktualizaci tento krok zopakuj."),
            Qr = "qr_ipa.png",
            QrCaption = L.T("Schoolday for iPhone", "Schoolday pro iPhone"),
            Extra = delegate (SetupForm f, FlowLayoutPanel p)
            {
                AddAction(p, L.T("Open the download link here", "Otevřít odkaz ke stažení tady"), false, 300, delegate { Open(IpaUrl); });
            },
        });

        // A free Apple Account signs apps for 7 days. SideStore renews them on the phone, with Wi-Fi
        // and LocalDevVPN on, through its "Refresh All Apps" Shortcuts action; a daily personal
        // automation runs it without anyone having to remember.
        steps.Add(new Step
        {
            Eyebrow = n(7),
            Title = L.T("Keep it renewing by itself", "Ať se obnovuje samo"),
            Body = L.T(
                "Apps from SideStore stop opening after 7 days unless SideStore renews them. Your Apple Account doesn't change; only the app's signature does. Let the iPhone renew it every day:\n\n" +
                "1.  Open the Shortcuts app, go to Automation and tap +.\n" +
                "2.  Choose Time of Day, pick a time you're usually home on Wi-Fi (e.g. 19:00), set it to Daily and choose Run Immediately. Tap Next.\n" +
                "3.  Tap New Blank Automation, then Add Action. Search for LocalDevVPN and add the action that turns the VPN on.\n" +
                "4.  Search for SideStore and add Refresh All Apps.\n" +
                "5.  Add LocalDevVPN's action that turns the VPN off again, so it isn't left on all day.\n" +
                "6.  Tap Done.\n\n" +
                "Renewing needs Wi-Fi, mobile data isn't enough. If it ever misses, Schoolday warns you 3 days before it would stop: open SideStore with LocalDevVPN on and tap the 7 DAYS button.",
                "Aplikace ze SideStoru se po 7 dnech přestanou otevírat, pokud je SideStore neobnoví. Účet Apple se nemění, jen podpis aplikace. Nech iPhone, ať ho obnovuje každý den:\n\n" +
                "1.  Otevři aplikaci Zkratky, přejdi na Automatizace a klepni na +.\n" +
                "2.  Vyber Denní doba, zvol čas, kdy býváš doma na Wi-Fi (třeba 19:00), nastav Denně a Spustit okamžitě. Klepni na Další.\n" +
                "3.  Klepni na Nová prázdná automatizace a pak na Přidat akci. Vyhledej LocalDevVPN a přidej akci, která VPN zapne.\n" +
                "4.  Vyhledej SideStore a přidej Refresh All Apps.\n" +
                "5.  Přidej akci LocalDevVPN, která VPN zase vypne, ať nezůstane zapnutá celý den.\n" +
                "6.  Klepni na Hotovo.\n\n" +
                "Obnova potřebuje Wi-Fi, mobilní data nestačí. Kdyby se někdy nepovedla, Schoolday tě 3 dny předem upozorní: otevři SideStore se zapnutou LocalDevVPN a klepni na 7 DAYS."),
        });

        steps.Add(new Step
        {
            Eyebrow = n(8),
            Title = L.T("Clean up this PC (optional)", "Ukliď počítač (nepovinné)"),
            Body = L.T(
                "With SideStore on your iPhone, this PC isn't needed any more: the phone renews Schoolday by itself. You can remove what this setup installed. " +
                "If SideStore ever has to be reinstalled, run this setup again and it downloads them back.\n\n" +
                "Keep iTunes if you use it for music or iPhone backups. Ticked programs are removed with their own uninstallers.",
                "Když je SideStore v iPhonu, počítač už není potřeba: telefon si Schoolday obnovuje sám. Můžeš odebrat, co tahle instalace nainstalovala. " +
                "Kdyby bylo někdy potřeba SideStore nainstalovat znovu, spusť tuhle instalaci a vše se znovu stáhne.\n\n" +
                "iTunes si nech, pokud je používáš na hudbu nebo zálohy iPhonu. Zaškrtnuté programy se odeberou jejich vlastními odinstalátory."),
            Checklist = true,
            Extra = delegate (SetupForm f, FlowLayoutPanel p)
            {
                AddAction(p, L.T("Remove selected", "Odebrat vybrané"), true, 240, f.RemoveSelected);
            },
        });
    }

    static void AddAction(FlowLayoutPanel p, string text, bool primary, int width, Action click)
    {
        var b = Ui.Button(text, primary);
        b.Width = width;
        b.Margin = new Padding(0, 0, 10, 0);
        b.Click += delegate { click(); };
        p.Controls.Add(b);
    }

    public void SetStatus(string text, Color color)
    {
        status.Text = text;
        status.ForeColor = color;
    }

    static void Open(string url)
    {
        try { Process.Start(new ProcessStartInfo(url) { UseShellExecute = true }); } catch (Exception) { }
    }

    static bool AppleDriverInstalled()
    {
        foreach (var name in new[] { "Apple Mobile Device Service", "AppleMobileDeviceService" })
        {
            using (var k = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\" + name))
                if (k != null) return true;
        }
        return File.Exists(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonProgramFiles), @"Apple\Mobile Device Support\AppleMobileDeviceService.exe"));
    }

    void SetBusy(bool value)
    {
        busy = value;
        next.Enabled = !value;
        back.Enabled = !value;
        actions.Enabled = !value;
        progress.Visible = value;
        if (value) progress.Value = 0;
    }

    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (busy) e.Cancel = true;
        base.OnFormClosing(e);
    }

    /// Downloads over HTTPS into a fresh temp folder, and checks the file really is what it should be.
    async Task<string> Download(string url, string fileName, byte[] magic)
    {
        string dir = Path.Combine(Path.GetTempPath(), "SchooldaySetup", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(dir);
        string path = Path.Combine(dir, fileName);
        using (var web = new WebClient())
        {
            web.Headers[HttpRequestHeader.UserAgent] = "SchooldaySetup/1.0";
            web.DownloadProgressChanged += delegate (object s, DownloadProgressChangedEventArgs e)
            {
                progress.Value = Math.Max(0, Math.Min(100, e.ProgressPercentage));
                status.Text = L.T("Downloading… ", "Stahuji… ") + (e.BytesReceived / (1024 * 1024)) + " MB";
            };
            await web.DownloadFileTaskAsync(new Uri(url), path);
        }
        var head = new byte[magic.Length];
        using (var f = File.OpenRead(path)) f.Read(head, 0, head.Length);
        for (int i = 0; i < magic.Length; i++)
            if (head[i] != magic[i]) { File.Delete(path); return null; }
        return path;
    }

    async void InstallITunes()
    {
        SetBusy(true);
        try
        {
            SetStatus(L.T("Downloading iTunes from Apple…", "Stahuji iTunes od Applu…"), Ui.Muted);
            string file = await Download(ITunesUrl, "iTunes64Setup.exe", new byte[] { (byte)'M', (byte)'Z' });
            if (file == null)
            {
                Open(ITunesUrl);
                SetStatus(L.T("Finish the download in your browser, then run it.", "Dokonči stahování v prohlížeči a spusť ho."), Ui.Muted);
                return;
            }
            SetStatus(L.T("Follow the iTunes installer, then come back here.", "Projdi instalací iTunes a pak se sem vrať."), Ui.Muted);
            await RunAndWait(new ProcessStartInfo(file) { UseShellExecute = true });
            bool ok = AppleDriverInstalled();
            SetStatus(ok ? L.T("iTunes is installed. You can continue.", "iTunes je nainstalované. Můžeš pokračovat.")
                         : L.T("The Apple driver still wasn't found. Try restarting the PC.", "Ovladač Apple se stále nenašel. Zkus restartovat počítač."), ok ? Ui.Good : Ui.Bad);
        }
        catch (Exception)
        {
            Open(ITunesUrl);
            SetStatus(L.T("The download failed, so I opened Apple's page in your browser.", "Stahování selhalo, proto jsem otevřel stránku Applu v prohlížeči."), Ui.Bad);
        }
        finally { SetBusy(false); }
    }

    async void InstallILoader()
    {
        SetBusy(true);
        try
        {
            string existing = FindILoader();
            if (existing == null)
            {
                SetStatus(L.T("Downloading iloader…", "Stahuji iloader…"), Ui.Muted);
                // Every MSI starts with the OLE compound-file signature.
                string msi = await Download(ILoaderMsi, "iloader-windows-x64.msi", new byte[] { 0xD0, 0xCF, 0x11, 0xE0 });
                if (msi == null) throw new IOException("Unexpected file");
                SetStatus(L.T("Follow the iloader installer…", "Projdi instalací iloaderu…"), Ui.Muted);
                await RunAndWait(new ProcessStartInfo("msiexec.exe", "/i \"" + msi + "\"") { UseShellExecute = true });
                existing = FindILoader();
            }
            if (existing != null)
            {
                Process.Start(new ProcessStartInfo(existing) { UseShellExecute = true });
                SetStatus(L.T("iloader is open. Continue with the steps above.", "iloader je otevřený. Pokračuj podle kroků výše."), Ui.Good);
            }
            else
            {
                SetStatus(L.T("Installed. Open iloader from the Start menu.", "Nainstalováno. Otevři iloader z nabídky Start."), Ui.Good);
            }
        }
        catch (Exception)
        {
            Open("https://iloader.app");
            SetStatus(L.T("The download failed, so I opened iloader's website.", "Stahování selhalo, proto jsem otevřel web iloaderu."), Ui.Bad);
        }
        finally { SetBusy(false); }
    }

    static string FindILoader()
    {
        var roots = new[]
        {
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs"),
        };
        foreach (var r in roots)
        {
            string p = Path.Combine(r, "iloader", "iloader.exe");
            if (File.Exists(p)) return p;
        }
        return null;
    }

    /// iTunes's parts in the order Apple recommends removing them, then iloader.
    static readonly string[] RemovableNames =
    {
        "iTunes", "Apple Software Update", "Apple Mobile Device Support", "Bonjour",
        "Apple Application Support (32-bit)", "Apple Application Support (64-bit)", "iloader",
    };

    static int RemovableRank(string name)
    {
        for (int i = 0; i < RemovableNames.Length; i++)
            if (name.Equals(RemovableNames[i], StringComparison.OrdinalIgnoreCase) ||
                (RemovableNames[i] == "iloader" && name.StartsWith("iloader", StringComparison.OrdinalIgnoreCase)))
                return i;
        return -1;
    }

    /// Reads Windows' list of installed programs (both registry views, machine and user).
    public static List<Removable> FindRemovable()
    {
        var found = new List<Removable>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (var hive in new[] { RegistryHive.LocalMachine, RegistryHive.CurrentUser })
            foreach (var view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                try
                {
                    using (var root = RegistryKey.OpenBaseKey(hive, view))
                    using (var list = root.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"))
                    {
                        if (list == null) continue;
                        foreach (var id in list.GetSubKeyNames())
                            using (var k = list.OpenSubKey(id))
                            {
                                if (k == null) continue;
                                var name = k.GetValue("DisplayName") as string;
                                var cmd = k.GetValue("UninstallString") as string;
                                if (string.IsNullOrEmpty(name) || string.IsNullOrEmpty(cmd)) continue;
                                if (RemovableRank(name) < 0 || !seen.Add(name)) continue;
                                found.Add(new Removable { Name = name, Command = cmd });
                            }
                    }
                }
                catch (Exception) { }
            }
        found.Sort(delegate (Removable a, Removable b) { return RemovableRank(a.Name).CompareTo(RemovableRank(b.Name)); });
        return found;
    }

    /// One command line for cmd.exe: MSI products quietly through msiexec, others as Windows lists
    /// them. "start /wait" makes each finish first: two MSI uninstalls at once would fail.
    static string UninstallLine(string command)
    {
        var guid = Regex.Match(command, @"\{[0-9A-Fa-f-]{36}\}");
        if (command.IndexOf("msiexec", StringComparison.OrdinalIgnoreCase) >= 0 && guid.Success)
            return "start \"\" /wait msiexec.exe /x " + guid.Value + " /passive /norestart";
        return "start \"\" /wait " + (command.StartsWith("\"") ? command : "\"" + command.Trim() + "\"");
    }

    void FillCleanup()
    {
        cleanup.Items.Clear();
        foreach (var r in FindRemovable()) cleanup.Items.Add(r, true);
        if (cleanup.Items.Count == 0)
            SetStatus(L.T("Nothing to clean up: iTunes and iloader aren't installed.", "Není co uklízet: iTunes ani iloader nejsou nainstalované."), Ui.Good);
    }

    async void RemoveSelected()
    {
        var chosen = new List<Removable>();
        foreach (var item in cleanup.CheckedItems) chosen.Add((Removable)item);
        if (chosen.Count == 0)
        {
            SetStatus(L.T("Nothing is ticked.", "Nic není zaškrtnuté."), Ui.Muted);
            return;
        }
        var names = new StringBuilder();
        foreach (var r in chosen) names.Append("•  ").Append(r.Name).Append('\n');
        var answer = MessageBox.Show(this,
            L.T("Remove these programs?\n\n", "Odebrat tyto programy?\n\n") + names +
            L.T("\nWindows asks for permission once.", "\nWindows se jednou zeptá na oprávnění."),
            Text, MessageBoxButtons.YesNo, MessageBoxIcon.Question);
        if (answer != DialogResult.Yes) return;

        SetBusy(true);
        try
        {
            SetStatus(L.T("Removing… follow any windows that appear.", "Odebírám… postupuj podle oken, která se objeví."), Ui.Muted);
            var lines = new List<string>();
            foreach (var r in chosen) lines.Add(UninstallLine(r.Command));
            // One elevated cmd runs them all in order, so there is a single permission prompt.
            var info = new ProcessStartInfo("cmd.exe", "/s /c \"" + string.Join(" & ", lines.ToArray()) + "\"")
            {
                UseShellExecute = true, Verb = "runas", WindowStyle = ProcessWindowStyle.Hidden,
            };
            await RunAndWait(info);
            FillCleanup();
            int left = 0;
            foreach (var r in chosen)
                foreach (var item in cleanup.Items)
                    if (((Removable)item).Name == r.Name) left++;
            SetStatus(left == 0 ? L.T("Done. This PC is cleaned up.", "Hotovo. Počítač je uklizený.")
                                : L.T("Some programs are still installed. Remove them in Settings › Apps.", "Některé programy zůstaly. Odeber je v Nastavení › Aplikace."),
                      left == 0 ? Ui.Good : Ui.Bad);
        }
        catch (Exception)
        {
            // Includes saying No to the permission prompt.
            SetStatus(L.T("Nothing was removed.", "Nic nebylo odebráno."), Ui.Muted);
        }
        finally { SetBusy(false); }
    }

    static Task RunAndWait(ProcessStartInfo info)
    {
        return Task.Run(delegate
        {
            using (var p = Process.Start(info)) { if (p != null) p.WaitForExit(); }
        });
    }

    /// Developer aid: saves every page as a PNG (no network, nothing installed).
    public void RenderTo(string dir)
    {
        StartPosition = FormStartPosition.Manual;
        Location = new Point(-4000, -4000);
        ShowInTaskbar = false;
        base.Show();
        for (int i = 0; i < steps.Count; i++)
        {
            Show(i);
            Application.DoEvents();
            using (var bmp = new Bitmap(Width, Height))
            {
                DrawToBitmap(bmp, new Rectangle(0, 0, Width, Height));
                bmp.Save(Path.Combine(dir, "step" + (i + 1) + ".png"), System.Drawing.Imaging.ImageFormat.Png);
            }
        }
    }
}

static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        var form = new SetupForm();
        if (args.Length >= 2 && args[0] == "--render") { form.RenderTo(args[1]); return; }
        if (args.Length >= 2 && args[0] == "--list-removable")
        {
            var lines = new List<string>();
            foreach (var r in SetupForm.FindRemovable()) lines.Add(r.Name + "\t" + r.Command);
            File.WriteAllLines(args[1], lines.ToArray());
            return;
        }
        Application.Run(form);
    }
}
