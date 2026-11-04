import {describe,it,expect,vi} from "vitest";
import {render,screen,waitFor} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {MemoryRouter} from "react-router-dom";
import VerifyEmail from "../pages/VerifyEmail";
import Earnings from "../marketplace/Earnings";
import {api} from "../services/api";
vi.mock("../services/api",()=>({api:vi.fn()}));
const mock=vi.mocked(api);
describe("verified campus flows",()=>{
 it("requires a six-digit code and verifies the email through the server",async()=>{mock.mockResolvedValue({message:"Verified"});render(<MemoryRouter initialEntries={[{pathname:"/verify-email",state:{email:"student@srmap.edu.in"}}]}><VerifyEmail/></MemoryRouter>);const user=userEvent.setup();await user.type(screen.getByLabelText("Verification code"),"123456");await user.click(screen.getByRole("button",{name:"Verify email"}));await waitFor(()=>expect(mock).toHaveBeenCalledWith("/api/auth/verify-otp","POST",{email:"student@srmap.edu.in",code:"123456"}));expect(await screen.findByText("You’re verified.")).toBeInTheDocument();});
 it("shows server OTP errors without granting access",async()=>{mock.mockRejectedValue(new Error("Incorrect code"));render(<MemoryRouter><VerifyEmail/></MemoryRouter>);const user=userEvent.setup();await user.type(screen.getByLabelText("University email"),"student@srmap.edu.in");await user.type(screen.getByLabelText("Verification code"),"000000");await user.click(screen.getByRole("button",{name:"Verify email"}));expect(await screen.findByText("Incorrect code")).toBeInTheDocument();expect(screen.queryByText("You’re verified.")).not.toBeInTheDocument();});
 it("separates earned, pending and self-reported collected amounts",async()=>{mock.mockResolvedValue({earned:80,pending:30,collected:50,jobs:[]});render(<MemoryRouter><Earnings/></MemoryRouter>);expect(await screen.findByText("₹80.00")).toBeInTheDocument();expect(screen.getByText("₹30.00")).toBeInTheDocument();expect(screen.getByText("₹50.00")).toBeInTheDocument();expect(screen.getByText(/no wallet, cash-out or automatic UPI verification/)).toBeInTheDocument();});
});
