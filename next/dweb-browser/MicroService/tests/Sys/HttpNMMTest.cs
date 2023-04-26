﻿using System.Text.RegularExpressions;

namespace DwebBrowser.MicroServiceTests;

public class HttpNMMTest
{
	[Theory]
	[InlineData("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15 dweb-host/dns.sys.dweb.localhost:22605 test")]
	[Trait("HttpNMM", "Regex")]
	public void RegexDwebHost_ReturnsDwebHostString(string str)
	{
		var reg = new Regex(@"\sdweb-host/(\S+)");
		//Debug.WriteLine("result: " + reg.Match(str).Value);
		MatchCollection matches = reg.Matches(str);
		Debug.WriteLine(String.Format("{0} matches found in: {1}", matches.Count, str));

        // Report on each match.
        foreach (Match match in matches)
        {
            GroupCollection groups = match.Groups;
            Debug.WriteLine(String.Format("result1: {0}", groups[1].Value));
			Assert.Equal("dns.sys.dweb.localhost:22605", groups[1].Value);
        }
    }
}

