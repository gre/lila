package lila.app
package mashup

import lila.api.Context
import lila.forum.MiniForumPost
import lila.game.Game
import lila.rating.PerfType
import lila.simul.Simul
import lila.timeline.Entry
import lila.tournament.{ Enterable, Winner }
import lila.tv.{ Featured, StreamOnAir }
import lila.user.User
import play.api.libs.json.JsObject

final class Preload(
    featured: Featured,
    leaderboard: Boolean => Fu[List[(User, PerfType)]],
    tourneyWinners: Int => Fu[List[Winner]],
    timelineEntries: String => Fu[List[Entry]],
    streamsOnAir: => () => Fu[List[StreamOnAir]],
    dailyPuzzle: () => Fu[Option[lila.puzzle.DailyPuzzle]],
    countRounds: () => Int,
    lobbyApi: lila.api.LobbyApi,
    geoIP: lila.security.GeoIP) {

  private type Response = (JsObject, List[Entry], List[MiniForumPost], List[Enterable], List[Simul], Option[Game], List[(User, PerfType)], List[Winner], Option[lila.puzzle.DailyPuzzle], List[StreamOnAir], List[lila.blog.MiniPost], Int, Boolean)

  def apply(
    posts: Fu[List[MiniForumPost]],
    tours: Fu[List[Enterable]],
    simuls: Fu[List[Simul]])(implicit ctx: Context): Fu[Response] =
    lobbyApi(ctx) zip
      posts zip
      tours zip
      simuls zip
      featured.one zip
      (ctx.userId ?? timelineEntries) zip
      leaderboard(true) zip
      tourneyWinners(10) zip
      dailyPuzzle() zip
      streamsOnAir() map {
        case (((((((((data, posts), tours), simuls), feat), entries), lead), tWinners), puzzle), streams) =>
          val inParis = ctx.me.?? { u =>
            u.profile.flatMap(_.country) ?? (_ == "FR") &&
              u.profile.flatMap(_.location) ?? (_.toLowerCase contains "paris")
          } || geoIP(ctx.req.remoteAddress) ?? { loc =>
            loc.country == "France" && (loc.region == Some("Paris") || loc.city == Some("Paris"))
          }
          (data, entries, posts, tours, simuls, feat, lead, tWinners, puzzle, streams, Env.blog.lastPostCache.apply, countRounds(), inParis)
      }
}
